package protocol.chrome

import cats.data.NonEmptyVector
import io.circe.Decoder

final case class ChromeProtocolEvent(
  name: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  parameters: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolEvent {
  implicit val decoder: Decoder[ChromeProtocolEvent] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      description <- c.downField("description").as[Option[String]]
      deprecated <- c.downField("deprecated").as[Deprecated]
      experimental <- c.downField("experimental").as[Experimental]
      parameters <- c.downField("parameters").as[Option[NonEmptyVector[ChromeProtocolTypeDefinition]]]
    } yield ChromeProtocolEvent(name, description, deprecated, experimental, parameters)
  }
}
