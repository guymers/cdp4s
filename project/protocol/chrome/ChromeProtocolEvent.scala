package protocol.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeProtocolEvent(
  name: String,
  description: Option[String],
  experimental: Option[Boolean],
  parameters: Option[Seq[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolEvent {
  implicit val decoder: Decoder[ChromeProtocolEvent] = deriveDecoder
}
