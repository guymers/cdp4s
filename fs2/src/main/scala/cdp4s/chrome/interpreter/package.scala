package cdp4s.chrome

import cats.ApplicativeError
import cats.effect.Concurrent
import cats.effect.Timer
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.http.ChromeHttpClient
import cdp4s.chrome.http.ChromeTab
import cdp4s.domain.Domains
import cdp4s.domain.Operations
import cdp4s.domain.event.Event
import cdp4s.domain.model.Target.TargetID
import cdp4s.ws.WsUri
import freestyle.free._
import fs2.Stream

package object interpreter {

  /**
    * Create a new tab, run the program and close the tab.
    *
    * If running against a headless Chrome the tab is private.
    */
  def runProgram[F[_], T](
    httpClient: ChromeHttpClient[F],
    program: FreeS[Operations.Op, T]
  )(implicit F: Concurrent[F], T: Timer[F]): Stream[F, Either[Event, T]] = {

    Stream.eval(httpClient.version())
      .flatMap {
        case v if v.Browser.startsWith("Headless") => withTargetTab(httpClient)
        case _ => withTab(httpClient)
      }
      .flatMap { wsUri =>
        WebSocketInterpreter.runWithEvents(httpClient.httpClient, wsUri, program)
      }
  }

  private def withTab[F[_]](
    httpClient: ChromeHttpClient[F]
  )(implicit AE: ApplicativeError[F, Throwable]): Stream[F, WsUri] = {

    Stream.bracket(httpClient.newTab())(tab => httpClient.closeTab(tab.id)).flatMap(resolveWebSocketDebuggerUrl[F])
  }

  private def withTargetTab[F[_]](
    httpClient: ChromeHttpClient[F]
  )(implicit F: Concurrent[F], T: Timer[F]): Stream[F, WsUri] = {

    def createTargetTab(uri: WsUri): F[ChromeTab] = {

      WebSocketInterpreter.run(httpClient.httpClient, uri, createTarget[Operations.Op]).flatMap { targetId =>
        httpClient.listTabs().flatMap[ChromeTab] { tabs =>
          val tab = tabs.find(_.id.id == targetId.value)
          tab.toRight[Throwable](ChromeHttpException.NoTab(targetId)).raiseOrPure
        }
      }
    }

    withTab(httpClient).flatMap { uri =>
      Stream.bracket(createTargetTab(uri))(tab => {
        val program = closeTarget[Operations.Op](TargetID(tab.id.id))
        WebSocketInterpreter.run(httpClient.httpClient, uri, program).map(_ => ())
      }).flatMap(resolveWebSocketDebuggerUrl[F])
    }
  }

  private def resolveWebSocketDebuggerUrl[F[_]](tab: ChromeTab)(
    implicit AE: ApplicativeError[F, Throwable]
  ) = Stream.eval {
    tab.webSocketDebuggerUrl.toRight[Throwable](ChromeHttpException.NoWebSocketDebuggerUrl(tab)).raiseOrPure
  }

  private def createTarget[F[_]](implicit domains: Domains[F]): FreeS[F, TargetID] = for {
    browserContextId <- domains.target.createBrowserContext
    targetId <- domains.target.createTarget("about:blank", browserContextId = Some(browserContextId))
  } yield targetId

  private def closeTarget[F[_]](targetId: TargetID)(implicit domains: Domains[F]): FreeS[F, Boolean] = for {
    result <- domains.target.closeTarget(targetId)
  } yield result

}
