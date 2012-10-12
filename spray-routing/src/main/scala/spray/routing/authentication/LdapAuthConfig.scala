/*
 * Copyright (C) 2011-2012 spray.io
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

import javax.naming.directory.SearchControls


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
trait LdapAuthConfig[T] {

  /**
   * The application-specific environment properties for the InitialLdapContext.
   * If the application uses 'simple' security authentication then the only required setting is the one configuring
   * the LDAP server and port:
   *
   * {{{javax.naming.Context.PROVIDER_URL -> "ldap://ldap.testathon.net:389"}}}
   *
   * However, you can set any of the properties defined in javax.naming.Context. (If a Context.SECURITY_PRINCIPAL
   * property is specified it overrides the one created by the `securityPrincipal` method).
   *
   * In addition to configuring the properties with this method the application can also choose to have this method
   * return a `Seq.empty` and configure all settings in a `jndi.properties` file on the classpath. A combination of
   * the two is also allowed.
   */
  def contextEnv(user: String, pass: String): Seq[(String, String)]

  /**
   * Returns the credentials used to bind to the LDAP server in order to search for a matching user entry.
   * For example:
   *
   * {{{val searchCredentials = "CN=stuart,OU=users,DC=testathon,DC=net" -> "stuart"}}}
   */
  def searchCredentials: (String, String)

  /**
   * The DN of the entity to base the directory search on.
   * For example:
   *
   * {{{def searchBase(user: String) = "OU=users,DC=testathon,DC=net"}}}
   */
  def searchBase(user: String): String

  /**
   * The search filter to use for searching for the user entry.
   * For example:
   *
   * {{{def searchFilter(user: String) = "(uid=%s)" format user}}}
   */
  def searchFilter(user: String): String

  /**
   * Configures the given searchControls instance according the application-specific requirements.
   * For example:
   *
   * {{{
   * searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
   * searchControls.setReturningAttributes(Array("givenName", "sn"))
   * }}}
   */
  def configureSearchControls(searchControls: SearchControls, user: String)

  /**
   * Creates a user object from the given LDAP query result.
   * The method can also choose to return None, in which case authentication will fail.
   */
  def createUserObject(queryResult: LdapQueryResult): Option[T]
}

case class LdapQueryResult(
  name: String,
  fullName: String,
  className: String,
  relative: Boolean,
  obj: AnyRef,
  attrs: Map[String, LdapAttribute]
)

case class LdapAttribute(
  id: String,
  ordered: Boolean,
  values: Seq[String]
) {
  def value = if (values.isEmpty) "" else values.head
}