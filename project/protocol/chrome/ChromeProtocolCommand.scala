package protocol.chrome

import cats.data.NonEmptyVector
import io.circe.Decoder

final case class ChromeProtocolCommand(
  name: String,
  description: Option[String],
  experimental: Option[Boolean],
  parameters: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
  returns: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolCommand {
  implicit val decoder: Decoder[ChromeProtocolCommand] = {
    Decoder.forProduct5("name", "description", "experimental", "parameters", "returns")(ChromeProtocolCommand.apply)
  }
}
