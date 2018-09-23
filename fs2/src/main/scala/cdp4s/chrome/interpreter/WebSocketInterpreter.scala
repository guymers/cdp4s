package cdp4s.chrome.interpreter

import cats.effect.Concurrent
import cats.effect.Timer
import cats.syntax.either._
import cats.syntax.flatMap._
import cdp4s.chrome.http.ChromeWebSocketClient
import cdp4s.domain.Operations
import cdp4s.domain.event.Event
import cdp4s.ws.WsUri
import freestyle.free.FreeS
import fs2.Stream
import spinoco.fs2.http.HttpClient

object WebSocketInterpreter {

  /**
    * Run a program returning its result.
    */
  def run[F[_], T](
    httpClient: HttpClient[F],
    wsUri: WsUri,
    program: FreeS[Operations.Op, T],
    initialSequence: Int = 0
  )(implicit F: Concurrent[F], T: Timer[F]): F[T] = {
    runWithEvents(httpClient, wsUri, program, initialSequence).collect {
      case Right(v) => v
    }.compile.last.flatMap { o =>
      o.toRight[Throwable](new RuntimeException("TODO: stream returned no values")).raiseOrPure
    }
  }

  /**
    * Run a program returning its result and any events that it may generate.
    */
  def runWithEvents[F[_], T](
    httpClient: HttpClient[F],
    wsUri: WsUri,
    program: FreeS[Operations.Op, T],
    initialSequence: Int = 0,
    requestQueueSize: Int = 100
  )(implicit F: Concurrent[F], T: Timer[F]): Stream[F, Either[Event, T]] = {

    Stream.eval {
      ChromeWebSocketClient(httpClient, wsUri, initialSequence, requestQueueSize)
    }.flatMap { client =>

      val eventHandlers = scala.collection.mutable.Buffer.empty[PartialFunction[Event, FreeS[Operations.Op, Unit]]]

      val chromeWebSocketHandlersImplicits = new ChromeWebSocketHandlers[F](client, eventHandlers)

      val programStream: Stream[F, T] = Stream.eval {
        import freestyle.free._
        import freestyle.free.implicits._
        import chromeWebSocketHandlersImplicits._

        program.interpret[F] : F[T]
      }

      client.connect.map(Left(_)) mergeHaltBoth programStream.map(Right(_))
    }
  }

}
