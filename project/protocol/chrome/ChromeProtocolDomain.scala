package protocol.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeProtocolDomain(
  domain: String,
  dependencies: Option[Seq[String]],
  commands: Seq[ChromeProtocolCommand],
  types: Option[Seq[ChromeProtocolTypeDescription]],
  events: Option[Seq[ChromeProtocolEvent]],
  experimental: Option[Boolean]
)

object ChromeProtocolDomain {
  implicit val decoder: Decoder[ChromeProtocolDomain] = deriveDecoder
}
