/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray
package authentication

import java.util.Hashtable
import javax.naming.{Context, NamingException, NamingEnumeration}
import javax.naming.ldap.InitialLdapContext
import javax.naming.directory.{SearchControls, SearchResult, Attribute}
import collection.JavaConverters._
import utils.Logging
import akka.dispatch.{Future, AlreadyCompletedFuture}

/**
 * The LdapAuthenticator faciliates user/password authentication against an LDAP server.
 * It delegates the application specific parts of the LDAP configuration to the given LdapAuthConfig instance,
 * which is also responsible for creating the object representing the application-specific user context.
 *
 * Authentication against an LDAP server is done in two separate steps:
 * First, some "search credentials" are used to log into the LDAP server and perform a search for the directory entry
 * matching a given user name. If exactly one user entry is found another LDAP bind operation is performed using the
 * principal DN of the found user entry to validate the password.
 */
class LdapAuthenticator[T](config: LdapAuthConfig[T]) extends UserPassAuthenticator[T] with Logging {

  def apply(userPass: Option[(String, String)]) = {
    def auth3(entry: LdapQueryResult, pass: String) = {
      ldapContext(entry.fullName, pass) match {
        case Right(authContext) =>
          authContext.close()
          config.createUserObject(entry)
        case Left(ex) =>
          log.info("Could not authenticate credentials '%s'/'%s': %s", entry.fullName, pass, ex)
          None
      }
    }

    def auth2(searchContext: InitialLdapContext, user: String, pass: String) = {
      query(searchContext, user) match {
        case entry :: Nil => auth3(entry, pass)
        case Nil =>
          log.warn("User '%s' not found (search filter '%s' and search base '%s'", user, config.searchFilter(user),
            config.searchBase(user))
          None
        case entries =>
          log.warn("Expected exactly one search result for search filter '%s' and search base '%s', but got %s",
            config.searchFilter(user), config.searchBase(user), entries.size)
          None
      }
    }

    def auth1(user: String, pass: String) = {
      val (searchUser, searchPass) = config.searchCredentials
      ldapContext(searchUser, searchPass) match {
        case Right(searchContext) =>
          val result = auth2(searchContext, user, pass)
          searchContext.close()
          result
        case Left(ex) =>
          log.warn("Could not authenticate with search credentials '%s'/'%s': %s", searchUser, searchPass, ex)
          None
      }
    }

    userPass match {
      case Some((user, pass)) => Future(auth1(user, pass))
      case None =>
        log.warn("LdapAuthenticator.apply called with empty userPass, authentication not possible")
        new AlreadyCompletedFuture(Right(None))
    }
  }

  def ldapContext(user: String, pass: String): Either[Throwable, InitialLdapContext] = {
    util.control.Exception.catching(classOf[NamingException]).either {
      val env = new Hashtable[AnyRef, AnyRef]
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
      env.put(Context.SECURITY_PRINCIPAL, user)
      env.put(Context.SECURITY_CREDENTIALS, pass)
      env.put(Context.SECURITY_AUTHENTICATION, "simple")
      for ((key, value) <- config.contextEnv(user, pass)) env.put(key, value)
      new InitialLdapContext(env, null)
    }
  }

  def query(ldapContext: InitialLdapContext, user: String): List[LdapQueryResult] = {
    val results: NamingEnumeration[SearchResult] = ldapContext.search(
      config.searchBase(user),
      config.searchFilter(user),
      searchControls(user)
    )
    results.asScala.toList.map(searchResult2LdapQueryResult)
  }

  def searchControls(user: String) = {
    val searchControls = new SearchControls
    config.configureSearchControls(searchControls, user)
    searchControls
  }

  def searchResult2LdapQueryResult(searchResult: SearchResult): LdapQueryResult = {
    import searchResult._
    LdapQueryResult(
      name = getName,
      fullName = getNameInNamespace,
      className = getClassName,
      relative = isRelative,
      obj = getObject,
      attrs = getAttributes.getAll.asScala.toSeq.map(a => a.getID -> attribute2LdapAttribute(a)) (collection.breakOut)
    )
  }

  def attribute2LdapAttribute(attr: Attribute): LdapAttribute = {
    LdapAttribute(
      id = attr.getID,
      ordered = attr.isOrdered,
      values = attr.getAll.asScala.toSeq.map(v => if (v != null) v.toString else "")
    )
  }
}

object LdapAuthenticator {
  def apply[T](config: LdapAuthConfig[T]) = new LdapAuthenticator(config)
}