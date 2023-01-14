package cdp4s

import io.circe.Decoder

package object interpreter {

  implicit val base64StringDecoder: Decoder[Array[Byte]] = Decoder[String].emap { str =>
    try {
      Right {
        java.util.Base64.getDecoder.decode(str)
      }
    } catch {
      case e: IllegalArgumentException => Left(e.getMessage)
    }
  }
}
