package cpd4s.example

import cats.data.Kleisli
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.syntax.show.*
import cdp4s.domain.Operation
import cpd4s.test.ChromeWebSocketInterpreterHelper
import fs2.Stream

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import scala.concurrent.duration.*

object Main extends IOApp {
  import Program.*

  private val Headless = true
  private val NumProcessors = Runtime.getRuntime.availableProcessors()

  private implicit val cdp4sRuntime: cdp4s.Runtime[IO] = cdp4s.Runtime.catsEffectIORuntime(runtime)

  override def run(args: List[String]) = {
    val errorDir = Resource.eval {
      IO.blocking {
        val errorDir = Files.createTempDirectory("cdp4s-example-errors")
        println(show"Error directory is ${errorDir.toFile.getAbsolutePath}")
        errorDir
      }
    }

    errorDir.use { errorDir =>
      stream(args, errorDir)
        .through { screenshot =>
          screenshot.evalMap { screenshot =>
            IO {
              println(show"${Instant.now().toString}: Screenshot at ${screenshot.toFile.getAbsolutePath}")
            }
          }
        }
        .compile
        .drain
        .timeout(30.seconds)
        .map((_: Unit) => ExitCode.Success)
    }
  }

  private def stream(args: List[String], errorDir: Path) = {

    val chromePath = Paths.get(args.headOption.getOrElse("/usr/bin/chromium"))
    val setup = ChromeWebSocketInterpreterHelper.create[IO](chromePath, Headless)

    // FIXME make the below nicer
    Stream.resource(setup).flatMap { interpreter =>
      val programs = List(
        Kleisli { (op: Operation[IO]) =>
          implicit val o: Operation[IO] = op
          takeScreenshot[IO](new URI("https://news.ycombinator.com/news"))
        },
//        Kleisli { implicit op: Operation[IO] => takeScreenshot[IO](new URI("https://reddit.com")) },
//        Kleisli { implicit op: Operation[IO] => search[IO]("test") },
//        Kleisli { implicit op: Operation[IO] => search[IO]("example") },
      )
      val programStreams = Stream.emits {
        programs
          .map { k =>
            Kleisli { (op: Operation[IO]) =>
              screenshotOnError(errorDir)(k.run(op))(implicitly, op)
            }
          }
          .map { k =>
            interpreter.run(op => k.run(op))
          }
          .map(Stream.eval)
      }.covary[IO]

      programStreams.parJoin(NumProcessors).timeout(30.seconds)
    }

  }

}
