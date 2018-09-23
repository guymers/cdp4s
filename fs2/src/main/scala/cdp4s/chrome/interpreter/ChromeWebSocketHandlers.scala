package cdp4s.chrome.interpreter

import scala.concurrent.duration._

import cats.effect.Concurrent
import cats.effect.Timer
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.http.ChromeWebSocketClient
import cdp4s.domain.Errors
import cdp4s.domain.Events
import cdp4s.domain.Operations
import cdp4s.domain.Util
import cdp4s.domain.event.Event
import freestyle.free.FreeS
import io.circe.Decoder
import io.circe.JsonObject

class ChromeWebSocketHandlers[F[_]](
  client: ChromeWebSocketClient[F],
  eventHandlers: scala.collection.mutable.Buffer[PartialFunction[Event, FreeS[Operations.Op, Unit]]]
)(implicit F: Concurrent[F], T: Timer[F]) extends cdp4s.domain.ChromeWebSocketHandlers[F] {

  implicit val UtilHandler: Util.Handler[F] = new Util.Handler[F] {

    def sleep(duration: FiniteDuration): F[Unit] = {
      T.sleep(duration)
    }

    def pure[T](v: T): F[T] = {
      F.pure(v)
    }

    def delay[T](v: Unit => T): F[T] = {
      F.delay(v(()))
    }

    def fail[T](t: Throwable): F[T] = {
      F.raiseError(t)
    }
  }

  implicit val EventsHandler: Events.Handler[F] = new Events.Handler[F] {

    def onEvent(f: PartialFunction[Event, FreeS[Operations.Op, Unit]]): F[Unit] = F.delay {
      eventHandlers.append(f)
    }

    def waitForEvent[E](f: PartialFunction[Event, E], timeout: FiniteDuration): F[Option[E]] = {
      F.race(
        T.sleep(timeout),
        client.eventStream.collectFirst(f).compile.last
      ).map {
        case Left(_) => None // None means the event did not come in time
        case Right(result) => result
      }
    }
  }

  implicit val ErrorsHandler: Errors.Handler[F] = new Errors.Handler[F] {

    def handle[T](fs: FreeS[Operations.Op, T], f: PartialFunction[Throwable, T]): F[T] = {
      handleWithF(fs)(f.andThen(F.pure))
    }

    def handleWith[T](fs: FreeS[Operations.Op, T], f: PartialFunction[Throwable, FreeS[Operations.Op, T]]): F[T] = {
      handleWithF(fs)(f.andThen(interpret))
    }

    private def handleWithF[T](fs: FreeS[Operations.Op, T])(f: PartialFunction[Throwable, F[T]]): F[T] = {
      interpret(fs).attempt.flatMap {
        case Left(t) => f.lift(t).getOrElse(F.raiseError(t))
        case Right(v) => F.delay(v)
      }
    }

    private def interpret[T](fs: FreeS[Operations.Op, T]): F[T] = {
      import freestyle.free._
      import freestyle.free.implicits._

      fs.interpret[F]
    }
  }

  def runCommand[T : Decoder](method: String, params: JsonObject): F[T] = {
    client.runCommand(method, params)
  }
}
