/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.routing
package authentication

import java.util.Hashtable
import javax.naming.{ Context, NamingException, NamingEnumeration }
import javax.naming.ldap.InitialLdapContext
import javax.naming.directory.{ SearchControls, SearchResult, Attribute }
import scala.collection.JavaConverters._
import akka.dispatch.{ Promise, Future, ExecutionContext }
import akka.actor.ActorSystem
import spray.util.LoggingContext

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
class LdapAuthenticator[T](config: LdapAuthConfig[T])(implicit ec: ExecutionContext, log: LoggingContext) extends UserPassAuthenticator[T] {

  def apply(userPassOption: Option[UserPass]) = {
    def auth3(entry: LdapQueryResult, pass: String) = {
      ldapContext(entry.fullName, pass) match {
        case Right(authContext) ⇒
          authContext.close()
          config.createUserObject(entry)
        case Left(ex) ⇒
          log.info("Could not authenticate credentials '{}'/'{}': {}", entry.fullName, pass, ex)
          None
      }
    }

    def auth2(searchContext: InitialLdapContext, userPass: UserPass) = {
      val UserPass(user, pass) = userPass
      query(searchContext, user) match {
        case entry :: Nil ⇒ auth3(entry, pass)
        case Nil ⇒
          log.warning("User '{}' not found (search filter '{}' and search base '{}'", user, config.searchFilter(user),
            config.searchBase(user))
          None
        case entries ⇒
          log.warning("Expected exactly one search result for search filter '{}' and search base '{}', but got {}",
            config.searchFilter(user), config.searchBase(user), entries.size)
          None
      }
    }

    def auth1(userPass: UserPass) = {
      val (searchUser, searchPass) = config.searchCredentials
      ldapContext(searchUser, searchPass) match {
        case Right(searchContext) ⇒
          val result = auth2(searchContext, userPass)
          searchContext.close()
          result
        case Left(ex) ⇒
          log.warning("Could not authenticate with search credentials '{}'/'{}': {}", searchUser, searchPass, ex)
          None
      }
    }

    userPassOption match {
      case Some(userPass) ⇒ Future(auth1(userPass))
      case None ⇒
        log.warning("LdapAuthenticator.apply called with empty userPass, authentication not possible")
        Promise.successful(None).future
    }
  }

  def ldapContext(user: String, pass: String): Either[Throwable, InitialLdapContext] = {
    scala.util.control.Exception.catching(classOf[NamingException]).either {
      val env = new Hashtable[AnyRef, AnyRef]
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
      env.put(Context.SECURITY_PRINCIPAL, user)
      env.put(Context.SECURITY_CREDENTIALS, pass)
      env.put(Context.SECURITY_AUTHENTICATION, "simple")
      for ((key, value) ← config.contextEnv(user, pass)) env.put(key, value)
      new InitialLdapContext(env, null)
    }
  }

  def query(ldapContext: InitialLdapContext, user: String): List[LdapQueryResult] = {
    val results: NamingEnumeration[SearchResult] = ldapContext.search(
      config.searchBase(user),
      config.searchFilter(user),
      searchControls(user))
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
      attrs = getAttributes.getAll.asScala.toSeq.map(a ⇒ a.getID -> attribute2LdapAttribute(a))(collection.breakOut))
  }

  def attribute2LdapAttribute(attr: Attribute): LdapAttribute = {
    LdapAttribute(
      id = attr.getID,
      ordered = attr.isOrdered,
      values = attr.getAll.asScala.toSeq.map(v ⇒ if (v != null) v.toString else ""))
  }
}

object LdapAuthenticator {
  def apply[T](config: LdapAuthConfig[T])(implicit ec: ExecutionContext, log: LoggingContext) = new LdapAuthenticator(config)
}
