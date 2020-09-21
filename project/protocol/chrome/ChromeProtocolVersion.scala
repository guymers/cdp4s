package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocolVersion(
  major: String,
  minor: String,
)

object ChromeProtocolVersion {
  implicit val decoder: Decoder[ChromeProtocolVersion] = {
    Decoder.forProduct2("major", "minor")(ChromeProtocolVersion.apply)
  }
}
