/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

package spray.httpx.encoding

import spray.http.{ HttpRequest, HttpEntity }
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.zip.{ ZipException, GZIPInputStream, GZIPOutputStream }
import org.parboiled.common.FileUtils
import org.specs2.mutable.Specification
import spray.util._
import akka.util.ByteString

class GzipSpec extends Specification with CodecSpecSupport {

  "The Gzip codec" should {
    "properly encode a small string" in {
      streamGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly decode a small string" in {
      ourGunzip(streamGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly roundtip encode/decode a small string" in {
      ourGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly encode a large string" in {
      streamGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly decode a large string" in {
      ourGunzip(streamGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode a large string" in {
      ourGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode an HttpRequest" in {
      val request = HttpRequest(entity = HttpEntity(largeText))
      Gzip.decode(Gzip.encode(request)) === request
    }
    "provide a better compression ratio than the standard Gzipr/Gunzip streams" in {
      ourGzip(largeTextBytes).length must be_<(streamGzip(largeTextBytes).length)
    }
    "properly decode concatenated compressions" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), gzip("User!")).flatten) must readAs("Hello, dear User!")
    }
    "throw an error on corrupt input" in {
      ourGunzip(corruptGzipContent) must throwA[ZipException]("(invalid literal/length code|Corrupt data \\(CRC32 checksum error\\))")
    }
    "not throw an error if a subsequent block is corrupt" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), corruptGzipContent).flatten) must readAs("Hello, dear ")
    }
    "support chunked round-trip encoding/decoding" in {
      val chunks = largeTextBytes.grouped(512).toArray
      val comp = Gzip.newCompressor
      val decomp = Gzip.newDecompressor
      val chunks2 =
        chunks.map { chunk ⇒ decomp.decompress(comp.compress(chunk).flush()) } :+ decomp.decompress(comp.finish())
      chunks2.flatten must readAs(largeText)
    }
    "works for chunked compressed data of sizes just above 1024" in {
      val comp = new GzipCompressor
      val decomp = new GzipDecompressor

      val inputBytes = ByteString("""{"baseServiceURL":"http://www.acme.com","endpoints":{"assetSearchURL":"/search","showsURL":"/shows","mediaContainerDetailURL":"/container","featuredTapeURL":"/tape","assetDetailURL":"/asset","moviesURL":"/movies","recentlyAddedURL":"/recent","topicsURL":"/topics","scheduleURL":"/schedule"},"urls":{"aboutAweURL":"www.foobar.com"},"channelName":"Cool Stuff","networkId":"netId","slotProfile":"slot_1","brag":{"launchesUntilPrompt":10,"daysUntilPrompt":5,"launchesUntilReminder":5,"daysUntilReminder":2},"feedbackEmailAddress":"feedback@acme.com","feedbackEmailSubject":"Commends from User","splashSponsor":[],"adProvider":{"adProviderProfile":"","adProviderProfileAndroid":"","adProviderNetworkID":0,"adProviderSiteSectionNetworkID":0,"adProviderVideoAssetNetworkID":0,"adProviderSiteSectionCustomID":{},"adProviderServerURL":"","adProviderLiveVideoAssetID":""},"update":[{"forPlatform":"ios","store":{"iTunes":"www.something.com"},"minVer":"1.2.3","notificationVer":"1.2.5"},{"forPlatform":"android","store":{"amazon":"www.something.com","play":"www.something.com"},"minVer":"1.2.3","notificationVer":"1.2.5"}],"tvRatingPolicies":[{"type":"sometype","imageKey":"tv_rating_small","durationMS":15000,"precedence":1},{"type":"someothertype","imageKey":"tv_rating_big","durationMS":15000,"precedence":2}],"exts":{"adConfig":{"globals":{"#{adNetworkID}":"2620","#{ssid}":"usa_tveapp"},"iPad":{"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_ipad/shows","adSize":[{"#{height}":90,"#{width}":728}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_ipad&sz=1x1&t=&c=#{doubleclickrandom}"},"watchwithshowtile":{"adMobAdUnitID":"/2620/usa_tveapp_ipad/watchwithshowtile","adSize":[{"#{height}":120,"#{width}":240}]},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_ipad/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"iPadRetina":{"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_ipad/shows","adSize":[{"#{height}":90,"#{width}":728}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_ipad&sz=1x1&t=&c=#{doubleclickrandom}"},"watchwithshowtile":{"adMobAdUnitID":"/2620/usa_tveapp_ipad/watchwithshowtile","adSize":[{"#{height}":120,"#{width}":240}]},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_ipad/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"iPhone":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/home","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/shows","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/shows/#{SHOW_NAME}","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_iphone&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_iphone/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"iPhoneRetina":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/home","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/shows","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_iphone/shows/#{SHOW_NAME}","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_iphone&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_iphone/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"Tablet":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/home","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/shows","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/shows/#{SHOW_NAME}","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_androidtab&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_androidtab/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"TabletHD":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/home","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/shows","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_androidtab/shows/#{SHOW_NAME}","adSize":[{"#{height}":90,"#{width}":728},{"#{height}":50,"#{width}":320},{"#{height}":50,"#{width}":300}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_androidtab&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_androidtab/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"Phone":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_android/home","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_android/shows","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_android/shows/#{SHOW_NAME}","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_android&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_android/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}},"PhoneHD":{"home":{"adMobAdUnitID":"/2620/usa_tveapp_android/home","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"showlist":{"adMobAdUnitID":"/2620/usa_tveapp_android/shows","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"episodepage":{"adMobAdUnitID":"/2620/usa_tveapp_android/shows/#{SHOW_NAME}","adSize":[{"#{height}":50,"#{width}":300},{"#{height}":50,"#{width}":320}]},"launch":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_android&sz=1x1&t=&c=#{doubleclickrandom}"},"showpage":{"doubleClickCallbackURL":"http://pubads.g.doubleclick.net/gampad/ad?iu=/2620/usa_tveapp_android/shows/#{SHOW_NAME}&sz=1x1&t=&c=#{doubleclickrandom}"}}}}}""", "utf8")
      val compressed = comp.compress(inputBytes.toArray[Byte]).finish()

      val decompressed = decomp.decompress(compressed)
      ByteString(decompressed) === inputBytes
    }
  }

  def gzip(s: String) = ourGzip(s.getBytes("UTF8"))
  def ourGzip(bytes: Array[Byte]) = Gzip.newCompressor.compress(bytes).finish()
  def ourGunzip(bytes: Array[Byte]) = Gzip.newDecompressor.decompress(bytes)

  lazy val corruptGzipContent = {
    val content = gzip("Hello")
    content.update(14, 26.toByte)
    content
  }

  def streamGzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val gos = new GZIPOutputStream(output); gos.write(bytes); gos.close()
    output.toByteArray
  }

  def streamGunzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    FileUtils.copyAll(new GZIPInputStream(new ByteArrayInputStream(bytes)), output)
    output.toByteArray
  }

}
