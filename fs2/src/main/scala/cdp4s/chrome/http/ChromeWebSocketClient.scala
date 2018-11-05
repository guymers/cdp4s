package cdp4s.chrome.http

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.Concurrent
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.ChromeHttpException
import cdp4s.chrome.ChromeRequest
import cdp4s.chrome.ChromeResponse
import cdp4s.circe._
import cdp4s.domain.event.Event
import cdp4s.ws.WsUri
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Queue
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import spinoco.fs2.http.HttpClient
import spinoco.fs2.http.websocket.Frame

object ChromeWebSocketClient {

  def apply[F[_], T](
    httpClient: HttpClient[F],
    wsUri: WsUri,
    initialSequence: Int = 0,
    requestQueueSize: Int = 100
  )(implicit F: Concurrent[F]): F[ChromeWebSocketClient[F]] = {

    val initialize = for {
      requestQ <- Queue.bounded[F, ChromeRequest](requestQueueSize)
      eventTopic <- Topic[F, Option[Event]](None)
    } yield (requestQ, eventTopic)

    initialize.map { case (requestQ, eventTopic) =>
      new ChromeWebSocketClient[F](httpClient, wsUri, requestQ, eventTopic, initialSequence)
    }
  }

}

class ChromeWebSocketClient[F[_]](
  httpClient: HttpClient[F],
  wsUri: WsUri,
  requestQ: Queue[F, ChromeRequest],
  eventTopic: Topic[F, Option[Event]],
  initialSequence: Int
)(implicit F: Concurrent[F]) {

  private val sequence = new AtomicInteger(initialSequence)

  private val responseCallbacks = scala.collection.mutable.Map.empty[Int, Queue[F, Json]]

  def connect: Stream[F, Event] = {

    val webSocketStream: Stream[F, Unit] = httpClient.websocket(wsUri.wsRequest, pipe).flatMap {
      case None => Stream.emit(())
      case Some(response) => Stream.raiseError(ChromeHttpException.WebSocketConnection(response))
    }

    val stream = webSocketStream.map(Left(_)) mergeHaltBoth eventStream.map(Right(_))
    stream.collect {
      case Right(v) => v
    }
  }

  /**
    * Returns a stream of events emitted during the run.
    *
    * NOTE: If you use this method you must ensure that the stream is run.
    */
  def eventStream: Stream[F, Event] = {
    eventTopic.subscribe(1000).collect {
      case Some(e) => e
    }
  }

  private val pipe: Pipe[F, Frame[Json], Frame[Json]] = { inbound =>
    val out = requestQ.dequeue.map { request =>
      Frame.Text(request.asJson)
    }

    val in = inbound.map(_.a).flatMap { json =>

      def response: Option[Stream[F, Nothing]] = {
        val responseId = json.asObject.flatMap(_.apply("id")).flatMap(_.asNumber).flatMap(_.toInt)
        responseId.map { id =>
          responseCallbacks.remove(id) match {
            case None => Stream.empty
            case Some(callback) => Stream.eval_(callback.enqueue1(json))
          }
        }
      }

      def event: Option[Stream[F, Nothing]] = {
        val eventMethod = json.asObject.flatMap(_.apply("method")).flatMap(_.asString)
        eventMethod.map { _ =>
          json.as[ChromeEvent] match {
            case Left(err) => Stream.raiseError {
              ChromeHttpException.EventDecoding(json, err)
            }
            case Right(chromeEvent) => Stream.eval_ {
              eventTopic.publish1(Some(chromeEvent.params))
            }
          }
        }
      }

      val s: Stream[F, Nothing] = response orElse event getOrElse Stream.empty
      s ++ Stream.emit(Frame.Text(json))

    }

    out.concurrently(in)
  }

  def runCommand[T : Decoder](method: String, params: JsonObject): F[T] = for {
    id <- F.delay { sequence.incrementAndGet() }
    callback <- Queue.synchronous[F, Json]
    _ = responseCallbacks += (id -> callback)
    _ <- requestQ.enqueue1(ChromeRequest(id, method, params))
    json <- callback.dequeue1
    result <- parseJsonResponse(json).raiseOrPure[F]
  } yield result

  private def parseJsonResponse[T : Decoder](json: Json): Either[Throwable, T] = for {
    response <- json.as[ChromeResponse[T]].leftMap { err =>
      ChromeHttpException.ResponseDecoding(json, err)
    }
    result <- {
      response.error.map { error =>
        ChromeHttpException.Error(error).asLeft
      } orElse {
        response.result.map(_.asRight)
      } getOrElse {
        ChromeHttpException.InvalidResponse(json).asLeft
      }
    }
  } yield result
}
