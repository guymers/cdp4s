package cdp4s.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeError(
  code: Int,
  message: String,
  data: Option[String]
)

object ChromeError {
  implicit val decoder: Decoder[ChromeError] = deriveDecoder
}
