package cdp4s.chrome.interpreter

import cats.NonEmptyParallel
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.concurrent.Deferred
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
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
  )(implicit F: Concurrent[F], P: NonEmptyParallel[F]): Resource[F, ChromeWebSocketInterpreter[F]] = for {
    eventListeners <- Resource.liftF {
      EventListenersPerSession.create[F]
    }
    eventTopic <- Resource.liftF {
      Topic[F, Option[ChromeEvent]](None)
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
      Resource.make(stream.compile.drain.start) { fiber =>
        client.shutdown >> fiber.join
      }
    }
    globalInterpreter = new ChromeWebSocketInterpreterImpl[F](client, None, eventListeners)
  } yield {

    new ChromeWebSocketInterpreter[F] {

      def run[T](program: Operation[F] => F[T]): F[T] = (for {
        sessionId <- cdp4s.domain.extensions.tab.createTab[F](F, globalInterpreter)
        _ <- Resource.make(F.unit)((_: Unit) => eventListeners.removeListeners(Some(sessionId)))
      } yield sessionId).use { sessionId =>
        runProgram(sessionId, program)
      }

      def runWithEvents[T](program: Operation[F] => F[T]): Stream[F, Either[Event, T]] = {
        Stream.resource(cdp4s.domain.extensions.tab.createTab[F](F, globalInterpreter)).flatMap { sessionId =>
          val eventStream = eventTopic.subscribe(1024).unNone.filter(_.sessionId.contains(sessionId)).map(_.event).map(Left(_))
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
        d.complete(e).recover {
          case _: IllegalStateException => ()
        }
      }
      index <- eventListeners.addListener(sessionId, complete)
    } yield {
      d.get.guarantee(eventListeners.removeListener(sessionId, index))
    }
  }

  override def runCommand[T : Decoder](method: String, params: JsonObject): F[T] = {
    client.runCommand(method, params, sessionId)
  }
}
