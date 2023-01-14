package cpd4s.example

import cats.NonEmptyParallel
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.effect.kernel.Temporal
import cats.effect.syntax.temporal.*
import cats.syntax.applicativeError.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration.*

object Program {

  def takeScreenshot[F[_]](
    uri: URI,
  )(implicit F: Async[F], P: NonEmptyParallel[F], op: Operation[F]): F[Path] = for {
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0d, mobile = false)
    _ <- PageHandle.navigate(uri).timeout(10.seconds)
    screenshot <- screenshotToTempFile
  } yield screenshot

  def search[F[_]](
    search: String,
  )(implicit F: Async[F], P: NonEmptyParallel[F], T: Temporal[F], op: Operation[F]): F[Path] = for {
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
        case false => T.sleep(100.milliseconds) >> untilVisible
      }
      untilVisible.timeout(5.seconds)
    }
    _ <- PageHandle.navigating(searchButton.click).timeout(10.seconds)

    screenshot <- screenshotToTempFile
  } yield screenshot

  def screenshotToTempFile[F[_]](implicit F: Sync[F], op: Operation[F]): F[Path] = for {
    file <- F.blocking {
      Files.createTempFile("cdp4s-example", ".png")
    }
    file <- cdp4s.domain.extensions.screenshot.take(file)
  } yield file

  def screenshotOnError[F[_], T](dir: Path)(
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
