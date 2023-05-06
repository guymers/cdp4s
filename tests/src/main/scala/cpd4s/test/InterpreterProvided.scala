package cpd4s.test

import cats.Parallel
import cats.effect.IO
import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import java.nio.file.Path
import java.nio.file.Paths

object InterpreterProvided {

  lazy val ChromePath: Path = Paths.get(sys.env.getOrElse("CHROME_PATH", "/usr/bin/chromium"))

  implicit val parallel: Parallel.Aux[IO, IO.Par] = IO.parallelForIO
}

@SuppressWarnings(Array("org.wartremover.warts.Null"))
trait InterpreterProvided extends BeforeAndAfterAll { self: Suite =>
  import InterpreterProvided.*
  import cats.effect.unsafe.implicits.global

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
    Option(release).foreach(_.unsafeRunSync())
    super.afterAll()
  }
}
