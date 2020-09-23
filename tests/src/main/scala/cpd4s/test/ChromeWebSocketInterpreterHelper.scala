package cpd4s.test

import java.nio.file.Path

import cats.Parallel
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Timer
import cdp4s.chrome.cli.ChromeLauncher
import cdp4s.chrome.interpreter.ChromeWebSocketClient
import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object ChromeWebSocketInterpreterHelper {
  import BlockerHelper.blocker

  val WebSocketBufferCapacity: Int = 1024

  def create[F[_]](
    chrome: Path,
    headless: Boolean,
  )(implicit F: ConcurrentEffect[F], P: Parallel[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeWebSocketInterpreter[F]] = {

    val launch = { // sudo sysctl -w kernel.unprivileged_userns_clone=1
      if (headless) ChromeLauncher.launchHeadless[F](blocker)(chrome, Set.empty)
      else ChromeLauncher.launch[F](blocker)(chrome, Set.empty)
    }
    for {
      instance <- launch
      backend <- AsyncHttpClientFs2Backend.resourceUsingConfig[F](
        new DefaultAsyncHttpClientConfig.Builder()
          .setWebSocketMaxFrameSize(10 * 1024 * 1024)
          .build(),
        blocker,
        webSocketBufferCapacity = Some(WebSocketBufferCapacity),
      )
      client <- ChromeWebSocketClient.connect[F](backend, instance.devToolsWebSocket, ChromeWebSocketClient.Options(
        requestQueueSize = WebSocketBufferCapacity,
      ))
      interpreter <- ChromeWebSocketInterpreter.create(client)
    } yield interpreter
  }

}
