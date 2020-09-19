package cdp4s.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeResponse[T](
  id: Long,
  error: Option[ChromeError],
  result: Option[T]
)

object ChromeResponse {
  implicit def decoder[T : Decoder]: Decoder[ChromeResponse[T]] = deriveDecoder
}
