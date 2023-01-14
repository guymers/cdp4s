package cpd4s.example

import cats.data.Kleisli
import cats.syntax.show.*
import cdp4s.domain.Operation
import cpd4s.test.ChromeWebSocketInterpreterHelper
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZIOAppArgs
import zio.durationInt
import zio.interop.catz.*
import zio.stream.ZStream

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

object MainZIO extends zio.ZIOAppDefault { self =>
  import Program.*

  private val Headless = true
  private val NumProcessors = Runtime.getRuntime.availableProcessors()

  private implicit val cdp4sRuntime: cdp4s.Runtime[zio.Task] = cdp4s.Runtime.zioRuntime(runtime)

  override def run = {
    val errorDir = ZIO.attemptBlocking {
      Files.createTempDirectory("cdp4s-example-errors")
    }.tap { path =>
      ZIO.logInfo(show"Error directory is ${path.toFile.getAbsolutePath}")
    }.withFinalizer { path =>
      ZIO.attemptBlocking {
        Files.deleteIfExists(path)
      }.ignoreLogged
    }

    for {
      args <- ZIOAppArgs.getArgs
      errorDir <- errorDir
      _ <- stream(args, errorDir)
        .tap { screenshot =>
          ZIO.logInfo(show"${Instant.now().toString}: Screenshot at ${screenshot.toFile.getAbsolutePath}")
        }
        .runDrain
        .timeout(30.seconds)
        .either
        .map { result =>
          println(result)
        }
    } yield ()
  }

  private def stream(args: Chunk[String], errorDir: Path) = {

    val chromePath = Paths.get(args.headOption.getOrElse("/usr/bin/chromium"))
    val setup = ChromeWebSocketInterpreterHelper.create[Task](chromePath, Headless)

    ZStream.scoped(setup.toScopedZIO).flatMap { interpreter =>
      val programs = List(
        Kleisli { implicit op: Operation[Task] =>
          takeScreenshot[Task](new URI("https://news.ycombinator.com/news"))
        },
//        Kleisli { implicit op: Operation[Task] => takeScreenshot[Task](new URI("https://reddit.com")) },
        Kleisli { implicit op: Operation[Task] => search[Task]("test") },
        //        Kleisli { implicit op: Operation[Task] => search[Task]("example") },
      )

      ZStream.fromIterable(programs).map { k =>
        Kleisli { (op: Operation[Task]) =>
          screenshotOnError(errorDir)(k.run(op))(implicitly, op)
        }
      }.mapZIOPar(NumProcessors) { k =>
        interpreter.run(op => k.run(op))
      }
    }
  }

}
