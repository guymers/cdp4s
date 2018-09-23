package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocolTypeDefinition(
  name: String,
  description: Option[String],
  `type`: ChromeProtocolType,
)

object ChromeProtocolTypeDefinition {
  implicit val decoder: Decoder[ChromeProtocolTypeDefinition] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      description <- c.downField("description").as[Option[String]]
      tpe <- Decoder[ChromeProtocolType].tryDecode(c)
    } yield ChromeProtocolTypeDefinition(name, description, tpe)
  }
}
