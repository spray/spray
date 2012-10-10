package cc.spray.can.spdy

/**
 * Contains spdy/2 protocol constants
 */
object Spdy2 {
  object ControlFrameTypes {
    val SYN_STREAM = 1
    val SYN_REPLY = 2
    val RST_STREAM = 3
    val SETTINGS = 4
    val NOOP = 5
    val PING = 6
    val GOAWAY = 7
    val HEADERS = 8
  }
  object Flags {
    import Conversions.flag

    val FLAG_FIN = flag(0x01)
    val FLAG_UNIDIRECTIONAL = flag(0x02)

    val FLAG_SETTINGS_CLEAR_PREVIOUSLY_PERSISTED_SETTINGS = flag(0x01)
  }
  object ErrorCodes {
    val PROTOCOL_ERROR = 1
    val UNSUPPORTED_VERSION = 4
  }

  val dictionary = (
    """|optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-
       |languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi
       |f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser
       |-agent10010120020120220320420520630030130230330430530630740040140240340440
       |5406407408409410411412413414415416417500501502503504505accept-rangesageeta
       |glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic
       |ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran
       |sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati
       |oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo
       |ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe
       |pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic
       |ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1
       |.1statusversionurl""".stripMargin.replaceAll("\n", "")+'\0').getBytes("ASCII")
}
