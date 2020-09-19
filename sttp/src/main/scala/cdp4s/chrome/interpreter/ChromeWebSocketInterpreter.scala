package cdp4s.chrome.interpreter

import scala.collection.mutable
import scala.concurrent.duration._

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.concurrent._
import cats.syntax.applicativeError._
import cats.syntax.functor._
import cats.syntax.option._
import cdp4s.domain.Events
import cdp4s.domain.Operation
import cdp4s.domain.Util
import cdp4s.domain.event.Event
import cdp4s.domain.model.Target.SessionID
import cdp4s.interpreter.WebSocketInterpreter
import fs2.Stream
import io.circe.Decoder
import io.circe.JsonObject

trait ChromeWebSocketInterpreter[F[_]] {

  def run[T](program: Operation[F] => F[T]): F[T]
  def runWithEvents[T](program: Operation[F] => F[T]): Stream[F, Either[Event, T]]
}

object ChromeWebSocketInterpreter {

  /**
    * Create an interpreter that can run programs.
    */
  def create[F[_]](
    client: ChromeWebSocketClient[F],
  )(implicit F: Concurrent[F], T: Timer[F]): Resource[F, ChromeWebSocketInterpreter[F]] = {
    // TODO run handlers on events
    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    val eventHandlers = mutable.Map.empty[Option[SessionID], mutable.Buffer[PartialFunction[Event, F[Unit]]]]

    val globalInterpreter = new ChromeWebSocketInterpreterImpl[F](client, None, eventHandlers)

    Resource.make(client.connect.compile.drain.start)(_.cancel).map { _ =>

      new ChromeWebSocketInterpreter[F] {

        def run[T](program: Operation[F] => F[T]): F[T] = {
          createTab[F](F, globalInterpreter).use { sessionId =>
            val interpreter = new ChromeWebSocketInterpreterImpl[F](client, sessionId.some, eventHandlers)
            program(interpreter)
          }
        }

        def runWithEvents[T](program: Operation[F] => F[T]): Stream[F, Either[Event, T]] = {
          Stream.resource(createTab[F](F, globalInterpreter)).flatMap { sessionId =>
            val interpreter = new ChromeWebSocketInterpreterImpl[F](client, sessionId.some, eventHandlers)
            val eventStream = client.eventStream.filter(_.sessionId.contains(sessionId)).map(_.params).map(Left(_))
            val programStream = Stream.eval(program(interpreter)).map(Right(_))
            eventStream.mergeHaltBoth(programStream)
          }
        }
      }
    }
  }

  private def createTab[F[_]](implicit F: Applicative[F], op: Operation[F]): Resource[F, SessionID] = for {
    sessionId <- cdp4s.domain.extensions.tab.createTab
  } yield sessionId

}

class ChromeWebSocketInterpreterImpl[F[_]] private[interpreter] (
  client: ChromeWebSocketClient[F],
  sessionId: Option[SessionID],
  eventHandlers: mutable.Map[Option[SessionID], mutable.Buffer[PartialFunction[Event, F[Unit]]]]
)(implicit F: Concurrent[F], T: Timer[F]) extends Operation[F] with WebSocketInterpreter[F] {

  override val util: Util[F] = new Util[F] {
    override def sleep(duration: FiniteDuration): F[Unit] = T.sleep(duration)
    override def pure[T](v: T): F[T] = F.pure(v)
    override def delay[T](v: Unit => T): F[T] = F.delay(v(()))
    override def fail[T](t: Throwable): F[T] = F.raiseError(t)
    override def handle[T](fs: F[T], f: PartialFunction[Throwable, T]): F[T] = fs.recover(f)
    override def handleWith[T](fs: F[T], f: PartialFunction[Throwable, F[T]]): F[T] = fs.recoverWith(f)
  }

  override val event: Events[F] = new Events[F] {
    override def onEvent(f: PartialFunction[Event, F[Unit]]): F[Unit] = {
      // TODO make thread safe
      F.delay {
        @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
        val buffer = eventHandlers.getOrElseUpdate(sessionId, mutable.Buffer.empty)
        val _ = buffer.append(f)
      }
    }

    override def waitForEvent[E](f: PartialFunction[Event, E], timeout: FiniteDuration): F[Option[E]] = {
      F.race(
        T.sleep(timeout),
        client.eventStream.filter(_.sessionId == sessionId).map(_.params).collectFirst(f).compile.last
      ).map {
        case Left(_) => None // None means the event did not come in time
        case Right(result) => result
      }
    }
  }

  override def runCommand[T : Decoder](method: String, params: JsonObject): F[T] = {
    client.runCommand(method, params, sessionId)
  }
}
