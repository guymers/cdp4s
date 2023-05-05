package cdp4s.chrome.interpreter

import cdp4s.Runtime
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import scala.util.Try
import cats.Parallel
import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.WebSocketException
import cdp4s.chrome.ChromeRequest
import cdp4s.chrome.ChromeResponse
import cdp4s.domain.model.Target.SessionID
import cdp4s.ws.WebSocketClient
import cdp4s.ws.WsUri
import fs2.Stream
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.syntax._

object ChromeWebSocketClient {

  final case class Options(
    requestQueueSize: Int,
  )

  def connect[F[_]](
    uri: WsUri,
    options: Options,
  )(implicit F: Async[F], P: Parallel[F], R: Runtime[F]): Resource[F, ChromeWebSocketClient[F]] = for {
    sequence <- Resource.eval {
      F.delay(new AtomicLong(0))
    }
    requestQ <- Resource.eval {
      Queue.bounded[F, Option[ChromeRequest]](options.requestQueueSize)
    }
    responseCallbacks <- Resource.eval {
      Ref.of(Map.empty[Long, Deferred[F, JsonObject]])
    }
    ws <- WebSocketClient.create[F](uri, WebSocketClient.Options.defaults)
    shuttingDown <- Resource.eval {
      Ref.of(false)
    }
  } yield {
    new ChromeWebSocketClient[F] {
      override def messages: Stream[F, Message] = createHandler(ws, requestQ, responseCallbacks)

      override def runCommand[T: Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
        _shuttingDown <- shuttingDown.get
        _: Unit <- if (_shuttingDown) F.raiseError(new WebSocketException.ShuttingDown()) else F.unit
        result <- _runCommand(method, params, sessionId)
      } yield result

      private def _runCommand[T: Decoder](method: String, params: JsonObject, sessionId: Option[SessionID]): F[T] = for {
        id <- F.delay { sequence.incrementAndGet() }
        callback <- Deferred.apply[F, JsonObject]
        _ <- responseCallbacks.update(_ + (id -> callback))
        _ <- requestQ.offer(Some(ChromeRequest(id, method, params, sessionId)))
        jsonObj <- callback.get
        result <- jsonObj.asJson.as[T].leftMap { err =>
          new WebSocketException.ResponseDecoding(jsonObj, err)
        }.liftTo[F]
      } yield result

      override def shutdown: F[Unit] = for {
        _: Unit <- shuttingDown.set(true)
        _: Unit <- requestQ.offer(None)
      } yield ()
    }
  }

  private def createHandler[F[_]](
    ws: WebSocketClient[F],
    requestQ: Queue[F, Option[ChromeRequest]],
    responseCallbacks: Ref[F, Map[Long, Deferred[F, JsonObject]]],
  )(implicit F: Concurrent[F], P: Parallel[F]) = {
    val out = Stream.fromQueueNoneTerminated(requestQ).evalMap { request =>
      ws.sendText(cdp4s.circe.print(request)).map((_: Unit) => request)
    }.map(Message.Outbound.apply) ++ Stream.exec {
      // wait for any outstanding response call backs before completing
      for {
        callbacks <- responseCallbacks.get
        _: Unit <- callbacks.values.map(_.get).toList.parSequence_
      } yield ()
    }

    val in = ws.inbound
      .evalMap(parseWebSocketData(_).liftTo[F])
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
                _ <- callback.complete(result)
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

  private def parseWebSocketData(data: WebSocketClient.Data) = data match {
    case WebSocketClient.Data.Text(str) =>
      cdp4s.circe.parser.decode[JsonObject](str).leftMap(new WebSocketException.FrameDecoding(str, _))
    case WebSocketClient.Data.Binary(bytes) =>
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
