package cpd4s.example

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.Executors

import scala.concurrent.duration._
import scala.util.control.NonFatal

import cats.Monad
import cats.NonEmptyParallel
import cats.data.Kleisli
import cats.effect.Blocker
import cats.effect.Concurrent
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.syntax.concurrent._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.parallel._
import cats.syntax.show._
import cdp4s.chrome.cli.ChromeLauncher
import cdp4s.chrome.interpreter.ChromeWebSocketClient
import cdp4s.chrome.interpreter.ChromeWebSocketInterpreter
import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle
import fs2.Stream
import fs2.internal.ThreadFactoriesExposed
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object Main extends IOApp {

  private val Headless = true

  private val NumProcessors = Runtime.getRuntime.availableProcessors()
  private val WebSocketBufferCapacity = 1024

  private val blocker = Blocker.liftExecutorService {
    val threadFactory = ThreadFactoriesExposed.named("blocking", daemon = true)
    Executors.newCachedThreadPool(threadFactory)
  }

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
    val launch = { // sudo sysctl -w kernel.unprivileged_userns_clone=1
      if (Headless) ChromeLauncher.launchHeadless[IO](blocker)(chromePath, Set.empty)
      else ChromeLauncher.launch[IO](blocker)(chromePath, Set.empty)
    }
    val setup = for {
      instance <- launch
      backend <- AsyncHttpClientFs2Backend.resourceUsingConfig[IO](
        new DefaultAsyncHttpClientConfig.Builder()
          .setWebSocketMaxFrameSize(10 * 1024 * 1024)
          .build(),
        blocker,
        webSocketBufferCapacity = Some(WebSocketBufferCapacity),
      )
      client <- Resource.liftF(ChromeWebSocketClient.create[IO](backend, instance.devToolsWebSocket, WebSocketBufferCapacity))
      interpreter <- ChromeWebSocketInterpreter.create(client)
    } yield interpreter

    // FIXME make the below nicer
    Stream.resource(setup).flatMap { interpreter =>
      val programs = List(
        Kleisli { op: Operation[IO] => takeScreenshot[IO](new URI("https://news.ycombinator.com/news"))(implicitly, IO.ioParallel, op) },
        Kleisli { op: Operation[IO] => takeScreenshot[IO](new URI("https://reddit.com"))(implicitly, IO.ioParallel, op) },
        Kleisli { op: Operation[IO] => searchGoogle[IO]("test")(implicitly, IO.ioParallel, implicitly, op) },
        Kleisli { op: Operation[IO] => searchGoogle[IO]("example")(implicitly, IO.ioParallel, implicitly, op) },
      )
      val programStreams = Stream.emits {
        programs
          .map { k =>
            Kleisli { op: Operation[IO] =>
              screenshotOnError(errorDir)(k.run(op))(implicitly, op)
            }
          }
          .map { k =>
            interpreter.run(op => k.run(op))
          }
          .map(Stream.eval)
      }.covary[IO]

      programStreams.parJoin(NumProcessors)
    }

  }

  private def takeScreenshot[F[_]: Concurrent : NonEmptyParallel](uri: URI)(implicit op: Operation[F]): F[Path] = {
    val timeout = 10.seconds

    for {
      _ <- initTab

      optPageHandle <- PageHandle.navigate(uri, timeout)
      _ <- optPageHandle.toRight(show"Failed to navigate to ${uri.toString}").leftMap(new RuntimeException(_)).liftTo[F]

      screenshot <- screenshotToTempFile
    } yield screenshot
  }

  private def searchGoogle[F[_]: Concurrent : NonEmptyParallel : Timer](search: String)(implicit op: Operation[F]): F[Path] = {
    val timeout = 10.seconds

    for {
      _ <- initTab

      optPageHandle <- PageHandle.navigate(new URI("https://duckduckgo.com"), timeout)
      pageHandle <- optPageHandle.toRight("Failed to navigate to DuckDuckGo").leftMap(new RuntimeException(_)).liftTo[F]

      optSearchTextElement <- pageHandle.find("input[type='text'][name='q']")
      searchTextElement <- optSearchTextElement.toRight("Failed to find search input").leftMap(new RuntimeException(_)).liftTo[F]
      _ <- searchTextElement.`type`(search)

      optSearchButton <- pageHandle.find("input[type='submit'][value='S']")
      searchButton <- optSearchButton.toRight("Failed to find search button").leftMap(new RuntimeException(_)).liftTo[F]
      _ <- {
        @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
        def untilVisible: F[Unit] = searchButton.isVisible.flatMap {
          case true => op.util.pure(())
          case false => op.util.sleep(100.milliseconds) >> untilVisible
        }
        untilVisible.timeout(5.seconds)
      }
      _ <- PageHandle.navigating(searchButton.click, timeout)

      screenshot <- screenshotToTempFile
    } yield screenshot
  }

  private def initTab[F[_]: Monad : NonEmptyParallel](implicit op: Operation[F]): F[Unit] = for {
    (_: Unit, _: Unit) <- (
      op.page.enable,
      op.runtime.enable,
    ).parTupled
    _: Unit <- op.emulation.setDeviceMetricsOverride(1280, 1024, 0.0D, mobile = false)
  } yield ()

  private def screenshotToTempFile[F[_]: Monad](implicit op: Operation[F]): F[Path] = for {
    data <- op.page.captureScreenshot(format = cdp4s.domain.model.Page.params.Format.png.some)
    file <- op.util.delay { _ =>
      Files.createTempFile("cdp4s-example", ".png")
    }
    file <- op.util.delay { _ =>
      Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  } yield file

  private def screenshotOnError[F[_]: Monad, T](dir: Path)(
    program: F[T]
  )(implicit op: Operation[F]): F[ T] = {

    def screenshotToDirectory(t: Throwable): F[ T] = {
      for {
        data <- op.page.captureScreenshot(format = cdp4s.domain.model.Page.params.Format.png.some)
        file <- op.util.delay { _ =>
          Files.createTempFile(dir, "error", ".png")
        }
        _ <- op.util.delay { _ =>
          Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        result <- op.util.fail[T](t)
      } yield result
    }

    for {
      result <- op.util.handleWith(program, {
        case NonFatal(e) => screenshotToDirectory(e)
      })
    } yield result
  }

}
