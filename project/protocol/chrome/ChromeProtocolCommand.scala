package protocol.chrome

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeProtocolCommand(
  name: String,
  description: Option[String],
  experimental: Option[Boolean],
  parameters: Option[Seq[ChromeProtocolTypeDefinition]],
  returns: Option[Seq[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolCommand {
  implicit val decoder: Decoder[ChromeProtocolCommand] = deriveDecoder
}
