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

import javax.naming.Context
import test.AbstractSprayTest
import http._
import HttpHeaders._
import javax.naming.directory.SearchControls

object LdapAuthenticatorTest extends AbstractSprayTest {
  args(ex = "suppress this test") // comment out to have this test run

  "The LdapAuthenticator" should {
    "be able to authenticate a user against a public LDAP testing server " in {
      test(HttpRequest(headers = List(Authorization(BasicHttpCredentials("stuart", "stuart"))))) {
        authenticate(httpBasic(authenticator = LdapAuthenticator(TestLdapAuthConfig))) { user =>
          completeWith(user.toString)
        }
      }.response.content.as[String] mustEqual Right("BasicUserContext(Stuart Lewis)")
    }
  }

  object TestLdapAuthConfig extends LdapAuthConfig[BasicUserContext] {
    def contextEnv(user: String, pass: String) = Seq(Context.PROVIDER_URL -> "ldap://ldap.testathon.net:389")
    def securityPrincipal(user: String) = "CN=%s,OU=users,DC=testathon,DC=net" format user
    def searchBase(user: String) = "OU=users,DC=testathon,DC=net"
    def configureSearchControls(searchControls: SearchControls, user: String) {
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
      searchControls.setReturningAttributes(Array("givenName", "sn"))
    }
    def searchFilter(user: String) = "cn=" + user
    def createUserObject(queryResult: Seq[LdapQueryResult]) =
      queryResult.headOption.map(r => BasicUserContext(r.attrs("givenName").value + ' ' + r.attrs("sn").value))
  }

}