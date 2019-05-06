package cdp4s.chrome

import cats.syntax.either._
import cats.instances.string._
import cats.syntax.show._
import cdp4s.domain.event.Event
import cdp4s.domain.model.Target.SessionID
import io.circe.Decoder
import io.circe.DecodingFailure

final case class ChromeEvent(
  method: String,
  params: Event,
  sessionId: Option[SessionID]
)

object ChromeEvent {

  implicit val decoder: Decoder[ChromeEvent] = Decoder.instance { c =>
    for {
      // leftMap so `cats.syntax.either._` is used on 2.12
      method <- c.downField("method").as[String].leftMap(identity)
      eventDecoder <- Event.decoders.get(method).toRight(
        DecodingFailure(show"No decoder for event '$method'", c.history)
      )
      params <- c.downField("params").as(eventDecoder)
      sessionId <- c.downField("sessionId").as[Option[SessionID]]
    } yield ChromeEvent(method, params, sessionId)
  }
}
