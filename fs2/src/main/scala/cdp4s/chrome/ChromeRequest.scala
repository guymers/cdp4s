package cdp4s.chrome

import cdp4s.domain.model.Target.SessionID
import io.circe.JsonObject
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder

final case class ChromeRequest(
  id: Long,
  method: String,
  params: JsonObject,
  sessionId: Option[SessionID]
)

object ChromeRequest {
  implicit val encoder: ObjectEncoder[ChromeRequest] = deriveEncoder
}
