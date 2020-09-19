package cdp4s.chrome.interpreter

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

import scala.util.Try

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.ChromeHttpException
import cdp4s.chrome.ChromeRequest
import cdp4s.chrome.ChromeResponse
import cdp4s.domain.model.Target.SessionID
import cdp4s.ws.WsUri
import fs2.Stream
import fs2.concurrent.Queue
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.syntax._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.ws.WebSocket
import sttp.ws.WebSocketFrame

object ChromeWebSocketClient {

  type Backend[F[_]] = SttpBackend[F, Fs2Streams[F] with sttp.capabilities.WebSockets]

  def create[F[_]](
    backend: Backend[F],
    wsUri: WsUri,
    requestQueueSize: Int,
  )(implicit F: Concurrent[F]): F[ChromeWebSocketClient[F]] = for {
      requestQ <- Queue.bounded[F, ChromeRequest](requestQueueSize)
    responseCallbacks <- Ref.of(Map.empty[Long, Deferred[F, JsonObject]])
    eventTopic <- Topic[F, Option[ChromeEvent]](None)
  } yield {
    new ChromeWebSocketClient[F](backend, wsUri, requestQ, responseCallbacks, eventTopic)
  }

}

final class ChromeWebSocketClient[F[_]] private (
  backend: ChromeWebSocketClient.Backend[F],
  wsUri: WsUri,
  requestQ: Queue[F, ChromeRequest],
  responseCallbacks: Ref[F, Map[Long, Deferred[F, JsonObject]]],
  val eventTopic: Topic[F, Option[ChromeEvent]],
)(implicit F: Concurrent[F]) {

  private val sequence = new AtomicLong(0)

  def connect: Stream[F, ChromeEvent] = {

    val webSocketRequest = basicRequest
      .response(asWebSocket(handler))
      .get(wsUri.uri)
      .send(backend)
    val webSocketStream = Stream.eval(webSocketRequest).flatMap { response =>
      response.body match {
        case Left(body) => Stream.raiseError(new ChromeHttpException.WebSocketConnection(response.code, body))
        case Right(()) => Stream.emit(())
      }
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
    eventTopic.subscribe(1024).collect {
      case Some(e) => e
    }
  }

  private def handler(ws: WebSocket[F]) = {
    val out = requestQ.dequeue.evalMap { request =>
      ws.sendText(cdp4s.circe.print(request))
    }

    val in = Stream.repeatEval(ws.receiveDataFrame())
      .flatMap {
        case Left(WebSocketFrame.Close(_, _)) => Stream.empty
        case Right(data) => Stream.apply(decodeWebSocketFrame(data))
      }
      .flatMap {
        case Left(e) => Stream.raiseError(e)
        case Right(e) => Stream.apply(e)
      }.flatMap { jsonObj =>

      def response: Option[Stream[F, Nothing]] = {
        val responseId = jsonObj.apply("id").flatMap(_.asNumber).flatMap(_.toLong)
        responseId.map { id =>
          Stream.eval {
            responseCallbacks.modify { map =>
              val callback = map.get(id)
              (map - id, callback)
            }
          }.flatMap {
            case None => Stream.empty
            case Some(callback) => Stream.eval_(callback.complete(jsonObj))
          }
        }
      }

      def event: Option[Stream[F, Nothing]] = {
        val eventMethod = jsonObj.apply("method").flatMap(_.asString)
        eventMethod.map { _ =>
          jsonObj.asJson.as[ChromeEvent] match {
            case Left(err) => Stream.raiseError {
              new ChromeHttpException.EventDecoding(jsonObj, err)
            }
            case Right(e) => Stream.eval_ {
              eventTopic.publish1(Some(e))
            }
          }
        }
      }

      response orElse event getOrElse Stream.empty
    }

    out.concurrently(in).compile.drain
  }

  def runCommand[T : Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
    id <- F.delay { sequence.incrementAndGet() }
    callback <- Deferred.apply[F, JsonObject]
    _ <- responseCallbacks.update(_ + (id -> callback))
    _ <- requestQ.enqueue1(ChromeRequest(id, method, params, sessionId))
    jsonObj <- callback.get
    result <- parseJsonResponse(jsonObj).liftTo[F]
  } yield result

  private def decodeWebSocketFrame(frame: WebSocketFrame.Data[_]) = frame match {
    case WebSocketFrame.Text(str, _, _) =>
      cdp4s.circe.parser.decode[JsonObject](str).leftMap(new ChromeHttpException.WebSocketFrameDecoding(str, _))
    case WebSocketFrame.Binary(bytes, _, _) =>
      cdp4s.circe.parser.decodeByteArray[JsonObject](bytes).leftMap { err =>
        val str = Try(new String(bytes, StandardCharsets.UTF_8)).toEither.fold(_.getMessage, identity)
        new ChromeHttpException.WebSocketFrameDecoding(str, err)
      }
  }

  private def parseJsonResponse[T : Decoder](jsonObj: JsonObject): Either[Throwable, T] = for {
    response <- jsonObj.asJson.as[ChromeResponse[T]].leftMap { err =>
      new ChromeHttpException.ResponseDecoding(jsonObj, err)
    }
    result <- {
      response.error.map { error =>
        new ChromeHttpException.Error(error).asLeft
      } orElse {
        response.result.map(_.asRight)
      } getOrElse {
        new ChromeHttpException.InvalidResponse(jsonObj.asJson).asLeft
      }
    }
  } yield result
}
