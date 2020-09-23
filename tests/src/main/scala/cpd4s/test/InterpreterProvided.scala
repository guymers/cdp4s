package cpd4s.test

import java.nio.file.Path
import java.nio.file.Paths

import cats.Parallel
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.effect.internals.IOAppPlatformExposed
import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

object InterpreterProvided {

  lazy val ChromePath: Path = Paths.get(sys.env.getOrElse("CHROME_PATH", "/usr/bin/chromium"))

  implicit val contextShift: ContextShift[IO] = IOAppPlatformExposed.defaultContextShift
  implicit val timer: Timer[IO] = IOAppPlatformExposed.defaultTimer
  implicit val parallel: Parallel.Aux[IO, IO.Par] = IO.ioParallel
}

@SuppressWarnings(Array("org.wartremover.warts.Null"))
trait InterpreterProvided extends BeforeAndAfterAll { self: Suite =>
  import InterpreterProvided._

  protected var interpreter: ChromeWebSocketInterpreter[IO] = _
  protected var release: IO[Unit] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val resource = ChromeWebSocketInterpreterHelper.create[IO](ChromePath, headless = true)
    val (_interpreter, _release) = resource.allocated.unsafeRunSync()
    interpreter = _interpreter
    release = _release
  }

  override protected def afterAll(): Unit = {
    if (release != null) {
      release.unsafeRunSync()
    }
    super.afterAll()
  }
}
