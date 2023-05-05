package cpd4s.example

import cats.NonEmptyParallel
import cats.Parallel
import cats.data.Kleisli
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.kernel.Async
import cats.effect.syntax.temporal.*
import cats.syntax.applicativeError.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.show.*
import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle
import cpd4s.test.ChromeWebSocketInterpreterHelper
import fs2.Stream

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import scala.concurrent.duration.*

object Main extends IOApp {

  private val Headless = true
  private val NumProcessors = Runtime.getRuntime.availableProcessors()

  private implicit val parallelIO: Parallel.Aux[IO, IO.Par] = IO.parallelForIO

  private implicit lazy val cdp4sRuntime: cdp4s.Runtime[IO] = cdp4s.Runtime.catsEffectIORuntime(runtime)

  override def run(args: List[String]): IO[ExitCode] = {
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
          takeScreenshot[IO](new URI("https://news.ycombinator.com/news"))(implicitly, implicitly, op)
        },
        Kleisli { (op: Operation[IO]) =>
          takeScreenshot[IO](new URI("https://reddit.com"))(implicitly, implicitly, op)
        },
        Kleisli { (op: Operation[IO]) => search[IO]("test")(implicitly, implicitly, op) },
        Kleisli { (op: Operation[IO]) => search[IO]("example")(implicitly, implicitly, op) },
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

  private def takeScreenshot[F[_]](
    uri: URI,
  )(implicit F: Async[F], P: NonEmptyParallel[F], op: Operation[F]): F[Path] = for {
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0d, mobile = false)
    _ <- PageHandle.navigate(uri).timeout(10.seconds)
    screenshot <- screenshotToTempFile
  } yield screenshot

  private def search[F[_]](
    search: String,
  )(implicit F: Async[F], P: NonEmptyParallel[F], op: Operation[F]): F[Path] = for {
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0d, mobile = false)
    pageHandle <- PageHandle.navigate(new URI("https://duckduckgo.com")).timeout(10.seconds)

    optSearchTextElement <- pageHandle.find("input[type='text'][name='q']")
    searchTextElement <-
      optSearchTextElement.toRight("Failed to find search input").leftMap(new RuntimeException(_)).liftTo[F]
    _ <- searchTextElement.`type`(search)

    optSearchButton <- pageHandle.find("input[type='submit'][value='S']")
    searchButton <- optSearchButton.toRight("Failed to find search button").leftMap(new RuntimeException(_)).liftTo[F]
    _ <- {
      @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
      def untilVisible: F[Unit] = searchButton.isVisible.flatMap {
        case true => F.unit
        case false => F.sleep(100.milliseconds) >> untilVisible
      }
      untilVisible.timeout(5.seconds)
    }
    _ <- PageHandle.navigating(searchButton.click).timeout(10.seconds)

    screenshot <- screenshotToTempFile
  } yield screenshot

  private def screenshotToTempFile[F[_]](implicit F: Sync[F], op: Operation[F]): F[Path] = for {
    file <- F.blocking {
      Files.createTempFile("cdp4s-example", ".png")
    }
    file <- cdp4s.domain.extensions.screenshot.take(file)
  } yield file

  private def screenshotOnError[F[_], T](dir: Path)(
    program: F[T],
  )(implicit F: Sync[F], op: Operation[F]): F[T] = {

    def screenshotToDirectory(t: Throwable): F[T] = {
      for {
        file <- F.blocking {
          Files.createTempFile(dir, "error", ".png")
        }
        _ <- cdp4s.domain.extensions.screenshot.take(file)
        result <- F.raiseError[T](t)
      } yield result
    }

    program.handleErrorWith(screenshotToDirectory)
  }

}
