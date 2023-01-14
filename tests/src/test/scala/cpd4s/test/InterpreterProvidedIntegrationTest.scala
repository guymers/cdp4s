package cpd4s.test

import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.durationInt
import zio.test.TestAspect
import zio.test.ZIOSpec

import java.nio.file.Paths

abstract class InterpreterProvidedIntegrationTest extends ZIOSpec[InterpreterProvidedIntegrationTest.Env] {

  val interpreter = ZIO.service[ChromeWebSocketInterpreter[Task]]

  override def bootstrap = InterpreterProvidedIntegrationTest.layer

  override def aspects = super.aspects ++ Chunk(
    TestAspect.withLiveEnvironment,
    TestAspect.timeout(30.seconds),
  )
}
object InterpreterProvidedIntegrationTest {
  import zio.interop.catz.*
  import zio.interop.catz.implicits.*

  type Env = ChromeWebSocketInterpreter[Task]

  val layer = ZLayer.scoped {
    for {
      pathStr <- zio.System.envOrElse("CHROME_PATH", "/usr/bin/chromium")
      path <- ZIO.attemptBlocking(Paths.get(pathStr))
      interpreter <- ChromeWebSocketInterpreterHelper.create[Task](path, headless = true).toScopedZIO
    } yield interpreter
  }
}
