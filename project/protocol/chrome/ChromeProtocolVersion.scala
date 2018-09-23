package protocol.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeProtocolVersion(
  major: String,
  minor: String
)

object ChromeProtocolVersion {
  implicit val decoder: Decoder[ChromeProtocolVersion] = deriveDecoder
}
