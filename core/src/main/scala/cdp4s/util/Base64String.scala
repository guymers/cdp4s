package cdp4s.util

import io.circe.Decoder
import io.circe.Encoder

import java.nio.charset.StandardCharsets

final case class Base64String(value: Array[Byte]) {
  override def equals(obj: Any): Boolean = obj match {
    case v: Base64String => value.sameElements(v.value)
    case _ => false
  }
}
object Base64String {

  implicit val decoder: Decoder[Base64String] = Decoder[String].emap { str =>
    try {
      val bytes = java.util.Base64.getDecoder.decode(str)
      Right(Base64String(bytes))
    } catch {
      case e: IllegalArgumentException => Left(e.getMessage)
    }
  }

  implicit val encoder: Encoder[Base64String] = Encoder[String].contramap { str =>
    val bytes = java.util.Base64.getEncoder.encode(str.value)
    new String(bytes, StandardCharsets.UTF_8)
  }
}
