package protocol.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeProtocol(
  domains: Seq[ChromeProtocolDomain],
  version: ChromeProtocolVersion
)

object ChromeProtocol {
  implicit val decoder: Decoder[ChromeProtocol] = deriveDecoder
}
