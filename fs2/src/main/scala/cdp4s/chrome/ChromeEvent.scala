package cdp4s.chrome

import cats.syntax.show.*
import cdp4s.domain.event.Event
import cdp4s.domain.model.Target.SessionID
import io.circe.Decoder
import io.circe.DecodingFailure

final case class ChromeEvent(
  method: String,
  event: Event,
  sessionId: Option[SessionID],
)

object ChromeEvent {

  implicit val decoder: Decoder[ChromeEvent] = Decoder.instance { c =>
    for {
      method <- c.downField("method").as[String]
      eventDecoder <- Event.decoders.get(method).toRight {
        DecodingFailure(show"No decoder for event '$method'", c.history)
      }
      event <- c.downField("params").as(eventDecoder)
      sessionId <- c.downField("sessionId").as[Option[SessionID]]
    } yield ChromeEvent(method, event, sessionId)
  }
}
