package protocol.chrome

import cats.data.NonEmptyVector
import io.circe.Decoder

final case class ChromeProtocolCommand(
  name: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  parameters: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
  returns: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolCommand {
  implicit val decoder: Decoder[ChromeProtocolCommand] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      description <- c.downField("description").as[Option[String]]
      deprecated <- c.downField("deprecated").as[Deprecated]
      experimental <- c.downField("experimental").as[Experimental]
      parameters <- c.downField("parameters").as[Option[NonEmptyVector[ChromeProtocolTypeDefinition]]]
      returns <- c.downField("returns").as[Option[NonEmptyVector[ChromeProtocolTypeDefinition]]]
    } yield ChromeProtocolCommand(name, description, deprecated, experimental, parameters, returns)
  }
}
