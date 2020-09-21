package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocol(
  domains: Vector[ChromeProtocolDomain],
  version: ChromeProtocolVersion,
)

object ChromeProtocol {
  implicit val decoder: Decoder[ChromeProtocol] = {
    Decoder.forProduct2("domains", "version")(ChromeProtocol.apply)
  }
}
