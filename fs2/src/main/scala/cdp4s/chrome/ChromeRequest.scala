package cdp4s.chrome

import cdp4s.domain.model.Target.SessionID
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.syntax.*

final case class ChromeRequest(
  id: Long,
  method: String,
  params: JsonObject,
  sessionId: Option[SessionID],
)

object ChromeRequest {
  implicit val encoder: Encoder.AsObject[ChromeRequest] = Encoder.AsObject.instance {
    case ChromeRequest(id, method, params, sessionId) => JsonObject(
        "id" -> id.asJson,
        "method" -> method.asJson,
        "params" -> params.asJson,
        "sessionId" -> sessionId.asJson,
      )
  }
}
