package cdp4s.chrome.cli

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration._
import cats.effect.Async
import cats.effect.Resource
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.effect.Sync
import cats.effect.kernel.Deferred
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cdp4s.chrome.ProcessException
import cdp4s.util.FileHelper
import cdp4s.ws.WsUri
import fs2.Stream

object ChromeLauncher {

  // DevTools listening on ws://127.0.0.1:33495/devtools/browser/3e350e57-ee17-4846-892a-cad6b03b6c92
  private val DevToolsListeningRegex = "^DevTools listening on (ws:\\/\\/.+)$".r

  private val PROCESS_START_TIMEOUT = 10.seconds

  /**
    * Launch a headless chrome process located at the given path.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def launchHeadless[F[_]](
    path: Path,
    extraArgs: Set[ChromeCLIArgument] = Set.empty
  )(implicit F: Async[F]): Resource[F, ChromeInstance] = {
    val arguments = ChromeCLIArgument.defaultArguments ++ ChromeCLIArgument.headlessArguments ++ extraArgs
    launchWithArguments(path, arguments)
  }

  /**
    * Launch a chrome process located at the given path.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def launch[F[_]](
    path: Path,
    extraArgs: Set[ChromeCLIArgument] = Set.empty
  )(implicit F: Async[F]): Resource[F, ChromeInstance] = {
    val arguments = ChromeCLIArgument.defaultArguments ++ extraArgs
    launchWithArguments(path, arguments)
  }

  /**
    * Launch a chrome process located at the given path with the given arguments.
    */
  def launchWithArguments[F[_]](
    path: Path,
    arguments: Set[ChromeCLIArgument],
  )(implicit F: Async[F]): Resource[F, ChromeInstance] = for {
    args <- ensureUserDataDirArgument[F](arguments)
    process <- Resource.make[F, Process](startChromeProcess(path, args))(stopChromeProcess(_))
    // read from the process, cannot cancel so need to do it in the background
    parsed <- Resource.eval(Deferred[F, Either[(String, String), WsUri]])
    _ <- processOutput(process)
      .evalTap {
        case Left(DevToolsListeningRegex(uri)) =>
          parsed.complete(WsUri.fromStr(uri).leftMap(uri -> _)).void
        case _ => F.unit
      }
      .drain
      .compile
      .drain
      .background
    // stop the process to stop the above read
    _ <- Resource.make[F, Unit](F.unit)(_ => stopChromeProcess(process))
    uri <- Resource.eval {
      F.race(parsed.get, stopChromeProcess(process).delayBy(PROCESS_START_TIMEOUT))
        .map {
          case Left(v) => Some(v)
          case Right(()) => None
        }
        .flatMap {
          case None => F.raiseError[WsUri](new ProcessException.Timeout(PROCESS_START_TIMEOUT))
          case Some(Left((uri, reason))) =>
            F.raiseError[WsUri](new ProcessException.InvalidDevToolsWebSocketUrl(uri, reason.some))
          case Some(Right(uri)) => F.pure(uri)
        }
    }
  } yield ChromeInstance(uri)

  private def processOutput[F[_]](
    process: Process,
  )(implicit F: Async[F]): Stream[F, Either[String, String]] = {

    def readInputStream(fis: F[InputStream]) = {
      fs2.io.readInputStream(fis, chunkSize = 4096, closeAfterUse = false)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
    }

    val err = readInputStream(F.delay(process.getErrorStream))
    val out = readInputStream(F.delay(process.getInputStream))
    err.map(Left(_)) mergeHaltBoth out.map(Right(_))
  }

  private def ensureUserDataDirArgument[F[_]](
    arguments: Set[ChromeCLIArgument],
  )(implicit F: Sync[F]) = {
    val hasUserDataDirArg = arguments.collectFirst {
      case ChromeCLIArgument.UserDataDir(_) => true
    }.getOrElse(false)

    val r = if (hasUserDataDirArg) {
      Resource.pure[F, Set[ChromeCLIArgument]](Set.empty)
    } else {
      val acquire = F.blocking {
        Files.createTempDirectory("cdp4s")
      }
      Resource.make(acquire) { dir =>
        F.blocking {
          val _ = FileHelper.deleteDirectory(dir)
        }
      }.map(dir => Set(ChromeCLIArgument.UserDataDir(dir)))
    }
    r.map(arguments ++ _)
  }

  private def startChromeProcess[F[_]](
    path: Path,
    arguments: Set[ChromeCLIArgument],
  )(implicit F: Sync[F]): F[Process] = for {
    executable <- F.blocking {
      Files.isExecutable(path)
    }
    _ <- if (!executable) F.raiseError {
      new ProcessException.NonExecutablePath(path)
    }
    else F.unit
    flags = arguments.map(_.flag).toList.sorted
    args = {
      val args = new java.util.ArrayList[String]()
      val _ = args.add(path.toFile.getAbsolutePath)
      flags.foreach(args.add(_))
      args
    }
    processBuilder = new ProcessBuilder(args)
    process <- F.blocking {
      processBuilder.start()
    }
  } yield process

  private def stopChromeProcess[F[_]](
    process: Process,
  )(implicit F: Async[F]): F[Unit] = {

    val s = Stream.eval {
      F.blocking {
        process.destroy()
      }
    } >> {
      val forceClose = Stream.sleep(5.seconds) >> Stream.eval(F.blocking {
        process.destroyForcibly()
      })
      val hasClosed = Stream.awakeEvery[F](200.milliseconds).map(_ => !process.isAlive)

      forceClose interruptWhen hasClosed
    } >> {
      val exitValue = process.exitValue()
      if (exitValue == 0) Stream.empty
      else Stream.raiseError(new ProcessException.Exit(exitValue))
    }
    s.compile.drain
  }

}
