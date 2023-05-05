package protocol.chrome

import cats.data.NonEmptyVector
import io.circe.Decoder

final case class ChromeProtocolDomain(
  domain: String,
  dependencies: Option[NonEmptyVector[String]],
  commands: NonEmptyVector[ChromeProtocolCommand],
  types: Option[NonEmptyVector[ChromeProtocolTypeDescription]],
  events: Option[NonEmptyVector[ChromeProtocolEvent]],
  experimental: Option[Boolean],
)

object ChromeProtocolDomain {
  implicit val decoder: Decoder[ChromeProtocolDomain] = {
    Decoder.forProduct6(
      "domain",
      "dependencies",
      "commands",
      "types",
      "events",
      "experimental",
    )(ChromeProtocolDomain.apply)
  }
}
