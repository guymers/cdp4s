package cdp4s.chrome

import io.circe.Decoder

final case class ChromeError(
  code: Int,
  message: String,
  data: Option[String],
)

object ChromeError {
  implicit val decoder: Decoder[ChromeError] = {
    Decoder.forProduct3("code", "message", "data")(ChromeError.apply)
  }
}
