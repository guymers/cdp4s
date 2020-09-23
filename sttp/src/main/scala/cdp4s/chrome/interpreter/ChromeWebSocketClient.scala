package cdp4s.chrome.interpreter

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

import scala.util.Try

import cats.Parallel
import cats.effect.Concurrent
import cats.effect.ExitCase
import cats.effect.Resource
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.WebSocketException
import cdp4s.chrome.ChromeRequest
import cdp4s.chrome.ChromeResponse
import cdp4s.domain.model.Target.SessionID
import cdp4s.ws.WsUri
import fs2.Stream
import fs2.concurrent.NoneTerminatedQueue
import fs2.concurrent.Queue
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.syntax._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.ws.WebSocket
import sttp.ws.WebSocketFrame

object ChromeWebSocketClient {

  type Backend[F[_]] = SttpBackend[F, Fs2Streams[F] with sttp.capabilities.WebSockets]

  final case class Options(
    requestQueueSize: Int,
  )

  def connect[F[_]](
    backend: Backend[F],
    uri: WsUri,
    options: Options,
  )(implicit F: Concurrent[F], P: Parallel[F]): Resource[F, ChromeWebSocketClient[F]] = for {
    sequence <- Resource.liftF {
      F.delay(new AtomicLong(0))
    }
    queue <- Resource.liftF {
      Queue.noneTerminated[F, Either[Throwable, Message]]
    }
    requestQ <- Resource.liftF {
      Queue.boundedNoneTerminated[F, ChromeRequest](options.requestQueueSize)
    }
    responseCallbacks <- Resource.liftF {
      Ref.of(Map.empty[Long, Deferred[F, JsonObject]])
    }
    _ <- connectToWebSocket(backend, uri, queue, requestQ, responseCallbacks).background
    shuttingDown <- Resource.liftF {
      Ref.of(false)
    }
  } yield {
    new ChromeWebSocketClient[F] {
      override def messages: Stream[F, Message] = queue.dequeue.evalMap(_.liftTo[F])

      override def runCommand[T: Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
        _shuttingDown <- shuttingDown.get
        _: Unit <- if (_shuttingDown) F.raiseError(new WebSocketException.ShuttingDown()) else F.unit
        result <- _runCommand(method, params, sessionId)
      } yield result

      private def _runCommand[T: Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
        id <- F.delay { sequence.incrementAndGet() }
        callback <- Deferred.apply[F, JsonObject]
        _ <- responseCallbacks.update(_ + (id -> callback))
        _ <- requestQ.enqueue1(Some(ChromeRequest(id, method, params, sessionId)))
        jsonObj <- callback.get
        result <- jsonObj.asJson.as[T].leftMap { err =>
          new WebSocketException.ResponseDecoding(jsonObj, err)
        }.liftTo[F]
      } yield result

      override def shutdown: F[Unit] = for {
        _: Unit <- shuttingDown.set(true)
        _: Unit <- requestQ.enqueue1(None)
      } yield ()
    }
  }

  private def connectToWebSocket[F[_]](
    backend: Backend[F],
    uri: WsUri,
    q: NoneTerminatedQueue[F, Either[Throwable, Message]],
    requestQ: NoneTerminatedQueue[F, ChromeRequest],
    responseCallbacks: Ref[F, Map[Long, Deferred[F, JsonObject]]],
  )(implicit F: Concurrent[F], P: Parallel[F]) = {
    val handler = (ws: WebSocket[F]) => {
      createHandler(ws, requestQ, responseCallbacks)
        .through(s => q.enqueue(s.map(Right(_)).map(Some(_))))
        .compile.drain
        .guaranteeCase {
          case ExitCase.Completed => q.enqueue1(None)
          case ExitCase.Error(e) => q.enqueue1(Some(Left(e)))
          case ExitCase.Canceled => q.enqueue1(None)
        }
    }

    basicRequest
      .response(asWebSocket(handler))
      .get(uri.value)
      .send(backend)
      .flatMap { response =>
        response.body
          .leftMap(new WebSocketException.Connection(response.code, _))
          .liftTo[F]
      }
  }

  private def createHandler[F[_]](
    ws: WebSocket[F],
    requestQ: NoneTerminatedQueue[F, ChromeRequest],
    responseCallbacks: Ref[F, Map[Long, Deferred[F, JsonObject]]],
  )(implicit F: Concurrent[F], P: Parallel[F]) = {
    val out = requestQ.dequeue.evalMap { request =>
      ws.sendText(cdp4s.circe.print(request)).map((_: Unit) => request)
    }.map(Message.Outbound.apply) ++ Stream.eval_ {
      // wait for any outstanding response call backs before completing
      for {
        callbacks <- responseCallbacks.get
        _: Unit <- callbacks.values.map(_.get).toList.parSequence_
      } yield ()
    }

    val in = Stream.repeatEval(ws.receiveDataFrame())
      .flatMap {
        case Left(WebSocketFrame.Close(_, _)) => Stream.empty
        case Right(data) => Stream.apply(parseWebSocketFrame(data))
      }
      .flatMap {
        case Left(e) => Stream.raiseError(e)
        case Right(e) => Stream.apply(e)
      }
      .evalMap { jsonObj =>
        def response = jsonObj.apply("id").map { _ =>
          jsonObj.asJson.as[ChromeResponse[JsonObject]]
            .leftMap(new WebSocketException.ResponseDecoding(jsonObj, _))
            .flatMap { response =>
              response.error.map { error =>
                new WebSocketException.Error(error).asLeft
              } orElse {
                response.result.map(response.id -> _).map(_.asRight)
              } getOrElse {
                new WebSocketException.InvalidResponse(jsonObj.asJson).asLeft
              }
            }
            .liftTo[F]
            .flatMap { case (id, result) =>
              for {
                callbackOpt <- responseCallbacks.modify { map =>
                  val callback = map.get(id)
                  (map - id, callback)
                }
                callback <- callbackOpt.toRight(new WebSocketException.NoCallback(id, result)).liftTo[F]
                _: Unit <- callback.complete(result)
              } yield Message.Inbound.response(id, result)
            }
        }

        def event = jsonObj.apply("method").map { _ =>
          jsonObj.asJson.as[ChromeEvent]
            .leftMap(new WebSocketException.EventDecoding(jsonObj, _))
            .liftTo[F]
            .map(event => Message.Inbound.event(event))
        }

        response orElse event getOrElse F.pure(Message.Inbound.unknown(jsonObj))
      }

    out.mergeHaltBoth(in)
  }

  private def parseWebSocketFrame(frame: WebSocketFrame.Data[_]) = frame match {
    case WebSocketFrame.Text(str, _, _) =>
      cdp4s.circe.parser.decode[JsonObject](str).leftMap(new WebSocketException.FrameDecoding(str, _))
    case WebSocketFrame.Binary(bytes, _, _) =>
      cdp4s.circe.parser.decodeByteArray[JsonObject](bytes).leftMap { err =>
        val str = Try(new String(bytes, StandardCharsets.UTF_8)).toEither.fold(_.getMessage, identity)
        new WebSocketException.FrameDecoding(str, err)
      }
  }

  sealed trait Message
  object Message {
    sealed trait Inbound extends Message
    object Inbound {
      final case class Response(id: Long, value: JsonObject) extends Inbound
      final case class Event(value: ChromeEvent) extends Inbound
      final case class Unknown(value: JsonObject) extends Inbound

      def response(id: Long, value: JsonObject): Inbound = Response(id, value)
      def event(value: ChromeEvent): Inbound = Event(value)
      def unknown(value: JsonObject): Inbound = Unknown(value)
    }

    final case class Outbound(value: ChromeRequest) extends Message
  }
}

trait ChromeWebSocketClient[F[_]] {
  def messages: Stream[F, ChromeWebSocketClient.Message]
  def runCommand[T : Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T]
  def shutdown: F[Unit]
}
