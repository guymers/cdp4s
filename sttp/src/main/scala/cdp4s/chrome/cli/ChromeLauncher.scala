package cdp4s.chrome.cli

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.duration._

import cats.effect.Blocker
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.Timer
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cdp4s.chrome.ChromeProcessException
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
  def launchHeadless[F[_]](blocker: Blocker)(
    path: Path,
    extraArgs: Set[ChromeCLIArgument] = Set.empty
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = {
    val arguments = ChromeCLIArgument.defaultArguments ++ ChromeCLIArgument.headlessArguments ++ extraArgs
    launchWithArguments(blocker)(path, arguments)
  }

  /**
    * Launch a chrome process located at the given path.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def launch[F[_]](blocker: Blocker)(
    path: Path,
    extraArgs: Set[ChromeCLIArgument] = Set.empty
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = {
    val arguments = ChromeCLIArgument.defaultArguments ++ extraArgs
    launchWithArguments(blocker)(path, arguments)
  }

  /**
    * Launch a chrome process located at the given path with the given arguments.
    */
  def launchWithArguments[F[_]](blocker: Blocker)(
    path: Path,
    arguments: Set[ChromeCLIArgument]
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = for {
    args <- ensureUserDataDirArgument[F](blocker)(arguments)
    process <- Resource.make[F, Process](startChromeProcess(blocker)(path, args))(stopChromeProcess(blocker)(_))
    uri <- Resource.liftF {
      F.race(
        T.sleep(PROCESS_START_TIMEOUT),
        processOutput(blocker)(process).collectFirst {
          case Left(DevToolsListeningRegex(uri)) => WsUri.fromStr(uri).leftMap(err => (uri, err.some))
        }.compile.last // there will only be one element due to `collectFirst`
      ).flatMap {
        case Left(_) | Right(None) => F.raiseError[WsUri](new ChromeProcessException.Timeout(PROCESS_START_TIMEOUT))
        case Right(Some(Left((uri, reason)))) => F.raiseError[WsUri](new ChromeProcessException.InvalidDevToolsWebSocketUrl(uri, reason))
        case Right(Some(Right(uri))) => F.pure(uri)
      }
    }
  } yield ChromeInstance(uri)

  private def processOutput[F[_]](blocker: Blocker)(
    process: Process,
  )(implicit F: Concurrent[F], CS: ContextShift[F]): Stream[F, Either[String, String]] = {

    def readInputStream(fis: F[InputStream]) = {
      fs2.io.readInputStream(fis, 4096, blocker, closeAfterUse = false)
        .through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
    }

    val err = readInputStream(F.delay(process.getErrorStream))
    val out = readInputStream(F.delay(process.getInputStream))
    err.map(Left(_)) mergeHaltBoth out.map(Right(_))
  }

  private def ensureUserDataDirArgument[F[_]](blocker: Blocker)(
    arguments: Set[ChromeCLIArgument],
  )(implicit F: Sync[F], CS: ContextShift[F]) = {
    val hasUserDataDirArg = arguments.collectFirst {
      case ChromeCLIArgument.UserDataDir(_) => true
    }.getOrElse(false)

    val r = if (hasUserDataDirArg) {
      Resource.pure(Set.empty)
    } else {
      val acquire = blocker.delay {
        Files.createTempDirectory("cdp4s")
      }
      Resource.make(acquire) { dir =>
        blocker.delay {
          val _ = FileHelper.deleteDirectory(dir)
        }
      }.map(dir => Set(ChromeCLIArgument.UserDataDir(dir)))
    }
    r.map(arguments ++ _)
  }

  private def startChromeProcess[F[_]](blocker: Blocker)(
    path: Path,
    arguments: Set[ChromeCLIArgument],
  )(implicit F: Sync[F], CS: ContextShift[F]): F[Process] = for {
    executable <- blocker.delay {
      Files.isExecutable(path)
    }
    _: Unit <- if (!executable) F.raiseError {
      new ChromeProcessException.NonExecutablePath(path)
    } else F.unit
    flags = arguments.map(_.flag).toList.sorted
    processBuilder = new ProcessBuilder(path.toFile.getAbsolutePath +: flags: _*)
    process <- blocker.delay {
      processBuilder.start()
    }
  } yield process

  private def stopChromeProcess[F[_]](blocker: Blocker)(
    process: Process,
  )(implicit F: Concurrent[F], CS: ContextShift[F], T: Timer[F]): F[Unit] = {

    val s = Stream.eval {
      blocker.delay {
        process.destroy()
      }
    } >> {
      val forceClose = Stream.sleep(5.seconds) >> Stream.eval(blocker.delay { process.destroyForcibly() })
      val hasClosed = Stream.awakeEvery[F](200.milliseconds).map(_ => !process.isAlive)

      forceClose interruptWhen hasClosed
    } >> {
      val exitValue = process.exitValue()
      if (exitValue == 0) Stream.empty
      else Stream.raiseError(new ChromeProcessException.Exit(exitValue))
    }
    s.compile.drain
  }

}
