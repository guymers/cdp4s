package cdp4s.chrome.interpreter

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.atomic.AtomicLong

import cats.effect.Concurrent
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Timer
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.ChromeHttpException
import cdp4s.chrome.ChromeRequest
import cdp4s.chrome.ChromeResponse
import cdp4s.circe._
import cdp4s.domain.model.Target.SessionID
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

  val DEFAULT_REQUEST_QUEUE_SIZE: Int = 1000

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply[F[_]](
    channelGroup: AsynchronousChannelGroup,
    wsUri: WsUri,
    initialSequence: Long = 0,
    requestQueueSize: Int = DEFAULT_REQUEST_QUEUE_SIZE
  )(implicit
    F: ConcurrentEffect[F],
    T: Timer[F],
    CS: ContextShift[F]
  ): F[ChromeWebSocketClient[F]] = {
    implicit val AG: AsynchronousChannelGroup = channelGroup
    spinoco.fs2.http.client[F]().flatMap { httpClient =>
      withHttpClient(httpClient, wsUri, initialSequence, requestQueueSize)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def withHttpClient[F[_]](
    httpClient: HttpClient[F],
    wsUri: WsUri,
    initialSequence: Long = 0,
    requestQueueSize: Int = DEFAULT_REQUEST_QUEUE_SIZE
  )(implicit F: Concurrent[F]): F[ChromeWebSocketClient[F]] = {

    val initialize = for {
      requestQ <- Queue.bounded[F, ChromeRequest](requestQueueSize)
      eventTopic <- Topic[F, Option[ChromeEvent]](None)
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
  eventTopic: Topic[F, Option[ChromeEvent]],
  initialSequence: Long
)(implicit F: Concurrent[F]) {

  private val sequence = new AtomicLong(initialSequence)

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private val responseCallbacks = scala.collection.mutable.Map.empty[Long, Queue[F, Json]]

  def connect: Stream[F, ChromeEvent] = {

    val webSocketStream = httpClient.websocket(wsUri.wsRequest, pipe).flatMap {
      case None => Stream.emit(())
      case Some(response) => Stream.raiseError(new ChromeHttpException.WebSocketConnection(response))
    }

    val stream = webSocketStream.map(Left(_)) mergeHaltBoth eventStream.map(Right(_))
    stream.collect {
      // TODO provide every event to a consumer if they want it
      case Right(v) => v
    }
  }

  /**
    * Returns a stream of events emitted during the run.
    *
    * NOTE: If you use this method you must ensure that the stream is run.
    */
  def eventStream: Stream[F, ChromeEvent] = {
    eventTopic.subscribe(10000).collect {
      case Some(e) => e
    }
  }

  private val pipe: Pipe[F, Frame[Json], Frame[Json]] = { inbound =>
    val out = requestQ.dequeue.map { request =>
      Frame.Text(request.asJson)
    }

    val in = inbound.map(_.a).flatMap { json =>

      def response: Option[Stream[F, Nothing]] = {
        val responseId = json.asObject.flatMap(_.apply("id")).flatMap(_.asNumber).flatMap(_.toLong)
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
              new ChromeHttpException.EventDecoding(json, err)
            }
            case Right(chromeEvent) => Stream.eval_ {
              eventTopic.publish1(Some(chromeEvent))
            }
          }
        }
      }

      val s: Stream[F, Nothing] = response orElse event getOrElse Stream.empty
      s ++ Stream.emit(Frame.Text(json))

    }

    out.concurrently(in)
  }

  def runCommand[T : Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
    id <- F.delay { sequence.incrementAndGet() }
    callback <- Queue.synchronous[F, Json]
    _ = responseCallbacks.update(id, callback)
    _ <- requestQ.enqueue1(ChromeRequest(id, method, params, sessionId))
    json <- callback.dequeue1
    result <- parseJsonResponse(json).raiseOrPure[F]
  } yield result

  private def parseJsonResponse[T : Decoder](json: Json): Either[Throwable, T] = for {
    response <- json.as[ChromeResponse[T]].leftMap { err =>
      new ChromeHttpException.ResponseDecoding(json, err)
    }
    result <- {
      response.error.map { error =>
        new ChromeHttpException.Error(error).asLeft
      } orElse {
        response.result.map(_.asRight)
      } getOrElse {
        new ChromeHttpException.InvalidResponse(json).asLeft
      }
    }
  } yield result
}
