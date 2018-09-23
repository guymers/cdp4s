package cdp4s.chrome

import cats.syntax.either._
import cats.instances.string._
import cats.syntax.show._
import cdp4s.domain.event.Event
import io.circe.Decoder
import io.circe.DecodingFailure

final case class ChromeEvent(
  method: String,
  params: Event
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
    } yield ChromeEvent(method, params)
  }
}
