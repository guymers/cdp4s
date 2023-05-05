package cdp4s.chrome.interpreter

import cats.NonEmptyParallel
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.syntax.monadCancel.*
import cats.effect.syntax.spawn.*
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.traverse.*
import cdp4s.chrome.ChromeEvent
import cdp4s.chrome.interpreter.ChromeWebSocketClient.Message
import cdp4s.domain.Events
import cdp4s.domain.Operation
import cdp4s.domain.event.Event
import cdp4s.domain.model.Target.SessionID
import cdp4s.interpreter.WebSocketInterpreter
import fs2.Stream
import fs2.concurrent.Topic
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
  )(implicit F: Async[F], P: NonEmptyParallel[F]): Resource[F, ChromeWebSocketInterpreter[F]] = for {
    eventListeners <- Resource.eval {
      EventListenersPerSession.create[F]
    }
    eventTopic <- Resource.eval {
      Topic[F, Option[ChromeEvent]]
    }
    _ <- {
      val stream = client.messages.evalTap {
        case Message.Outbound(_) => F.unit
        case Message.Inbound.Response(_, _) => F.unit
        case Message.Inbound.Event(e) => for {
            listeners <- eventListeners.listeners(e.sessionId)
            _: List[Unit] <- listeners.toList.flatMap(_.lift(e.event).toList).sequence // in series for now
            _ <- eventTopic.publish1(Some(e))
          } yield ()
        case Message.Inbound.Unknown(_) => F.unit
      }
      stream.compile.drain.background
        .onCancel(Resource.eval(client.shutdown))
    }
    globalInterpreter = new ChromeWebSocketInterpreterImpl[F](client, None, eventListeners)
  } yield {

    new ChromeWebSocketInterpreter[F] {

      override def run[T](program: Operation[F] => F[T]): F[T] = (for {
        sessionId <- cdp4s.domain.extensions.tab.createTab[F](F, globalInterpreter)
        _ <- Resource.make(F.unit)((_: Unit) => eventListeners.removeListeners(Some(sessionId)))
      } yield sessionId).use { sessionId =>
        runProgram(sessionId, program)
      }

      override def runWithEvents[T](program: Operation[F] => F[T]): Stream[F, Either[Event, T]] = {
        Stream.resource(cdp4s.domain.extensions.tab.createTab[F](F, globalInterpreter)).flatMap { sessionId =>
          val eventStream = eventTopic.subscribe(1024).unNone
            .filter(_.sessionId.contains(sessionId)).map(_.event).map(Left(_))
          val programStream = Stream.eval(runProgram(sessionId, program)).map(Right(_))
          eventStream.mergeHaltBoth(programStream)
        }
      }

      private def runProgram[T](id: SessionID, program: Operation[F] => F[T]): F[T] = {
        implicit val op: Operation[F] = new ChromeWebSocketInterpreterImpl[F](client, Some(id), eventListeners)
        cdp4s.domain.extensions.tab.initialize >> program(op)
      }
    }
  }
}

final class ChromeWebSocketInterpreterImpl[F[_]] private[interpreter] (
  client: ChromeWebSocketClient[F],
  sessionId: Option[SessionID],
  eventListeners: EventListenersPerSession[F],
)(implicit F: Concurrent[F]) extends Operation[F] with WebSocketInterpreter[F] {

  override val event: Events[F] = new Events[F] {
    override def onEvent(f: PartialFunction[Event, F[Unit]]): F[F[Unit]] = for {
      index <- eventListeners.addListener(sessionId, f)
    } yield eventListeners.removeListener(sessionId, index)

    override def waitForEvent[E](f: PartialFunction[Event, E]): F[F[E]] = for {
      d <- Deferred[F, E]
      complete = f.andThen { e =>
        d.complete(e).map(_ => ()).recover {
          case _: IllegalStateException => ()
        }
      }
      index <- eventListeners.addListener(sessionId, complete)
    } yield {
      d.get.guarantee(eventListeners.removeListener(sessionId, index))
    }
  }

  override def runCommand[T: Decoder](method: String, params: JsonObject): F[T] = {
    client.runCommand(method, params, sessionId)
  }
}
