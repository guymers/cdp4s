package cdp4s.chrome

import io.circe.Decoder

final case class ChromeResponse[T](
  id: Long,
  error: Option[ChromeError],
  result: Option[T],
)

object ChromeResponse {
  implicit def decoder[T: Decoder]: Decoder[ChromeResponse[T]] = {
    Decoder.forProduct3("id", "error", "result")(ChromeResponse.apply[T])
  }
}
