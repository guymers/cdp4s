package cpd4s.test

import cdp4s.Runtime
import java.nio.file.Path
import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import cdp4s.chrome.cli.ChromeLauncher
import cdp4s.chrome.interpreter.ChromeWebSocketClient
import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter

object ChromeWebSocketInterpreterHelper {

  val WebSocketBufferCapacity: Int = 1024

  def create[F[_]](
    chrome: Path,
    headless: Boolean,
  )(implicit F: Async[F], P: Parallel[F], R: Runtime[F]): Resource[F, ChromeWebSocketInterpreter[F]] = {

    val launch = { // sudo sysctl -w kernel.unprivileged_userns_clone=1
      if (headless) ChromeLauncher.launchHeadless[F](chrome, Set.empty)
      else ChromeLauncher.launch[F](chrome, Set.empty)
    }
    for {
      instance <- launch
      client <- ChromeWebSocketClient.connect[F](
        instance.devToolsWebSocket,
        ChromeWebSocketClient.Options(
        requestQueueSize = WebSocketBufferCapacity,
      ))
      interpreter <- ChromeWebSocketInterpreter.create(client)
    } yield interpreter
  }

}
