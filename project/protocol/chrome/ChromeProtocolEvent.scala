package protocol.chrome

import cats.data.NonEmptyVector
import io.circe.Decoder

final case class ChromeProtocolEvent(
  name: String,
  description: Option[String],
  experimental: Option[Boolean],
  parameters: Option[NonEmptyVector[ChromeProtocolTypeDefinition]],
)

object ChromeProtocolEvent {
  implicit val decoder: Decoder[ChromeProtocolEvent] = {
    Decoder.forProduct4("name", "description", "experimental", "parameters")(ChromeProtocolEvent.apply)
  }
}
