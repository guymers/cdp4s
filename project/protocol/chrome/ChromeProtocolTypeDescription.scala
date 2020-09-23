package protocol.chrome

import io.circe.Decoder

final case class ChromeProtocolTypeDescription(
  id: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  `type`: ChromeProtocolType,
)

object ChromeProtocolTypeDescription {
  implicit val decoder: Decoder[ChromeProtocolTypeDescription] = Decoder.instance { c =>
    for {
      id <- c.downField("id").as[String]
      description <- c.downField("description").as[Option[String]]
      deprecated <- c.downField("deprecated").as[Deprecated]
      experimental <- c.downField("experimental").as[Experimental]
      tpe <- Decoder[ChromeProtocolType].tryDecode(c)
    } yield ChromeProtocolTypeDescription(id, description, deprecated, experimental, tpe)
  }
}
