package cpd4s.example

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

import scala.concurrent.duration._

import cats.NonEmptyParallel
import cats.Parallel
import cats.data.Kleisli
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.syntax.concurrent._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle
import cpd4s.test.BlockerHelper
import cpd4s.test.ChromeWebSocketInterpreterHelper
import fs2.Stream

object Main extends IOApp {
  import BlockerHelper.blocker

  private val Headless = true
  private val NumProcessors = Runtime.getRuntime.availableProcessors()

  private implicit val parallelIO: Parallel.Aux[IO, IO.Par] = IO.ioParallel

  override def run(args: List[String]): IO[ExitCode] = {
    val errorDir = Resource.liftF {
      blocker.delay[IO, Path] {
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
        Kleisli { implicit op: Operation[IO] => takeScreenshot[IO](new URI("https://news.ycombinator.com/news")) },
        Kleisli { implicit op: Operation[IO] => takeScreenshot[IO](new URI("https://reddit.com")) },
        Kleisli { implicit op: Operation[IO] => search[IO]("test") },
        Kleisli { implicit op: Operation[IO] => search[IO]("example") },
      )
      val programStreams = Stream.emits {
        programs
          .map { k =>
            Kleisli { op: Operation[IO] =>
              screenshotOnError(errorDir)(k.run(op))(implicitly, implicitly, op)
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
    uri: URI
  )(implicit F: Concurrent[F], P: NonEmptyParallel[F], CS: ContextShift[F], T: Timer[F], op: Operation[F]): F[Path] = for {
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0D, mobile = false)
    _ <- PageHandle.navigate(uri).timeout(10.seconds)
    screenshot <- screenshotToTempFile
  } yield screenshot

  private def search[F[_]](
    search: String
  )(implicit F: Concurrent[F], P: NonEmptyParallel[F], CS: ContextShift[F], T: Timer[F], op: Operation[F]): F[Path] = for {
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0D, mobile = false)
    pageHandle <- PageHandle.navigate(new URI("https://duckduckgo.com")).timeout(10.seconds)

    optSearchTextElement <- pageHandle.find("input[type='text'][name='q']")
    searchTextElement <- optSearchTextElement.toRight("Failed to find search input").leftMap(new RuntimeException(_)).liftTo[F]
    _ <- searchTextElement.`type`(search)

    optSearchButton <- pageHandle.find("input[type='submit'][value='S']")
    searchButton <- optSearchButton.toRight("Failed to find search button").leftMap(new RuntimeException(_)).liftTo[F]
    _ <- {
      @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
      def untilVisible: F[Unit] = searchButton.isVisible.flatMap {
        case true => F.unit
        case false => T.sleep(100.milliseconds) >> untilVisible
      }
      untilVisible.timeout(5.seconds)
    }
    _ <- PageHandle.navigating(searchButton.click).timeout(10.seconds)

    screenshot <- screenshotToTempFile
  } yield screenshot

  private def screenshotToTempFile[F[_]](implicit F: Sync[F], CS: ContextShift[F], op: Operation[F]): F[Path] = for {
    file <- blocker.delay {
      Files.createTempFile("cdp4s-example", ".png")
    }
    file <- cdp4s.domain.extensions.screenshot.take(blocker, file)
  } yield file

  private def screenshotOnError[F[_], T](dir: Path)(
    program: F[T]
  )(implicit F: Sync[F], CS: ContextShift[F], op: Operation[F]): F[T] = {

    def screenshotToDirectory(t: Throwable): F[T] = {
      for {
        file <- blocker.delay {
          Files.createTempFile(dir, "error", ".png")
        }
        _ <- cdp4s.domain.extensions.screenshot.take(blocker, file)
        result <- F.raiseError[T](t)
      } yield result
    }

    program.handleErrorWith(screenshotToDirectory)
  }

}
