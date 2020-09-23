package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocolTypeDefinition(
  name: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  `type`: ChromeProtocolType,
)

object ChromeProtocolTypeDefinition {
  implicit val decoder: Decoder[ChromeProtocolTypeDefinition] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      description <- c.downField("description").as[Option[String]]
      deprecated <- c.downField("deprecated").as[Deprecated]
      experimental <- c.downField("experimental").as[Experimental]
      tpe <- Decoder[ChromeProtocolType].tryDecode(c)
    } yield ChromeProtocolTypeDefinition(name, description, deprecated, experimental, tpe)
  }
}
