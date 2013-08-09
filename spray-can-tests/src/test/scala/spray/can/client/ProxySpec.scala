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

package spray.can.client

import org.specs2.mutable.Specification
import com.typesafe.config.ConfigFactory._
import spray.http.Uri

class ProxySpec extends Specification {

  val http = Uri.httpScheme()

  "ProxySupport" should {
    def proxytest(proxyConf: String)(host: String) = {
      val proxySettings = ProxySettings(parseString(proxyConf), Map.empty[String, String])
      ProxySupport.proxyFor(host, proxySettings.get(http))
    }

    "ignore missing proxy settings" in {
      proxytest("""
        http = none
        https = none""")("example.com") === None
    }
    "use existing proxy settings" in {
      proxytest("""
        http {
          host = some-proxy.com
          port = 8080
          non-proxy-hosts = []
        }
        https = none""")("example.com") === Some("some-proxy.com", 8080)
    }
    "correctly ignore non-proxy-hosts" in {
      val withIgnore = proxytest("""
        http {
          host = some-proxy.com
          port = 8080
          non-proxy-hosts = ["*.suffix", "prefix.*", ignored.net]
        }
        https = none""") _
      withIgnore("example.com") === Some("some-proxy.com", 8080)
      withIgnore("test.suffix") === None
      withIgnore("prefix.test") === None
      withIgnore("ignored.net") === None
    }
  }

  "The Proxy Settings" should {
    val testConf = load().getConfig("spray.can.client.proxy")

    val sampleProperties = Map(
      "http.proxyHost" -> "myproxy.com",
      "http.proxyPort" -> "8080",
      "http.nonProxyHosts" -> "*.internal.net|www.company.*")

    "default to empty" in {
      ProxySettings(testConf, Map.empty[String, String]) ===
        Map.empty[String, ProxySettings]
    }

    "use existing system settings when set to 'default'" in {
      ProxySettings(testConf, sampleProperties) ===
        Map(http -> ProxySettings("myproxy.com", 8080, List("*.internal.net", "www.company.*")))
    }

    "ignore system settings when set to 'none'" in {
      ProxySettings(parseString("""
        http = none
        https = none"""), sampleProperties) === Map.empty[String, ProxySettings]
    }

    "correctly use configured proxy settings" in {
      ProxySettings(parseString("""
        http {
          host = another-proxy.com
          port = 9090
          non-proxy-hosts = ["*.ignore.me"]
        }
        https = none"""), Map.empty[String, String]) ===
        Map(http -> ProxySettings("another-proxy.com", 9090, List("*.ignore.me")))
    }
  }
}
