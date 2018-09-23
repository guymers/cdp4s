package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocolTypeDescription(
  id: String,
  description: Option[String],
  `type`: ChromeProtocolType,
)

object ChromeProtocolTypeDescription {
  implicit val decoder: Decoder[ChromeProtocolTypeDescription] = Decoder.instance { c =>
    for {
      id <- c.downField("id").as[String]
      description <- c.downField("description").as[Option[String]]
      tpe <- Decoder[ChromeProtocolType].tryDecode(c)
    } yield ChromeProtocolTypeDescription(id, description, tpe)
  }
}
