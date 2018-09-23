package cdp4s.chrome

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ChromeRequest(id: Int, method: String, params: JsonObject)

object ChromeRequest {
  implicit val encoder: Encoder[ChromeRequest] = deriveEncoder
}
