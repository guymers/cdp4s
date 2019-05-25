package cdp4s.chrome.cli

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.duration._

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
  def launchHeadless[F[_]](
    path: Path,
    extraArgs: Set[ChromeCLIArgument] = Set.empty
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = {
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
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = {
    val arguments = ChromeCLIArgument.defaultArguments ++ extraArgs
    launchWithArguments(path, arguments)
  }

  /**
    * Launch a chrome process located at the given path with the given arguments.
    */
  def launchWithArguments[F[_]](
    path: Path,
    arguments: Set[ChromeCLIArgument]
  )(implicit F: Concurrent[F], T: Timer[F], CS: ContextShift[F]): Resource[F, ChromeInstance] = {

    Resource.make(startChromeProcess(path, arguments)) {
      case (process, tempUserDataDir) => stopChromeProcess(process, tempUserDataDir)
    }.flatMap { case (process, _) =>
      Resource.liftF(
        F.race(
          T.sleep(PROCESS_START_TIMEOUT),
          processOutput(process).collectFirst {
            case Left(DevToolsListeningRegex(uri)) => WsUri.fromStr(uri).leftMap(err => (uri, err.messageWithContext.some))
          }.compile.last // there will only be one element due to `collectFirst`
        ).flatMap {
          case Left(_) | Right(None) => F.raiseError[WsUri](new ChromeProcessException.Timeout(PROCESS_START_TIMEOUT))
          case Right(Some(Left((uri, reason)))) => F.raiseError[WsUri](new ChromeProcessException.InvalidDevToolsWebSocketUrl(uri, reason))
          case Right(Some(Right(uri))) => F.pure(uri)
        }.map { uri =>
          ChromeInstance(uri)
        }
      )
    }
  }

  private def processOutput[F[_]](
    process: Process
  )(implicit F: Concurrent[F], CS: ContextShift[F]): Stream[F, Either[String, String]] = {

    def readInputStream(inputStream: InputStream) = {
      // readInputStreamAsync will block a thread on the ec it uses
      val blockingExecutionContext = scala.concurrent.ExecutionContext.global
      fs2.io.readInputStream(F.delay(inputStream), 4096, blockingExecutionContext, closeAfterUse = false)
        .through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
    }

    val err = readInputStream(process.getErrorStream)
    val out = readInputStream(process.getInputStream)
    err.map(Left(_)) mergeHaltBoth out.map(Right(_))
  }

  private def startChromeProcess[F[_]](
    path: Path,
    arguments: Set[ChromeCLIArgument]
  )(implicit F: Sync[F]): F[(Process, Option[Path])] = {

    F.delay(Files.isExecutable(path)).flatMap { executable =>
      if (!executable) F.raiseError {
        new ChromeProcessException.NonExecutablePath(path)
      } else F.delay {
        val hasUserDataDirArg = arguments.collectFirst {
          case ChromeCLIArgument.UserDataDir(_) => true
        }.getOrElse(false)

        val (args, tempUserDataDir) = if (hasUserDataDirArg) {
          (arguments, None)
        } else {
          val tempUserDataDir = Files.createTempDirectory("cdp4s")
          (arguments + ChromeCLIArgument.UserDataDir(tempUserDataDir), Some(tempUserDataDir))
        }

        val flags = args.map(_.flag).toSeq.sorted
        val processBuilder = new ProcessBuilder(path.toFile.getAbsolutePath +: flags: _*)
        val process = processBuilder.start()
        (process, tempUserDataDir)
      }
    }
  }

  private def stopChromeProcess[F[_]](
    process: Process,
    tempUserDataDir: Option[Path]
  )(implicit F: Concurrent[F], T: Timer[F]): F[Unit] = {

    val s = Stream.eval {
      F.delay {
        process.destroy()
      }
    } >> {
      val forceClose = Stream.sleep(5.seconds) >> Stream.eval(F.delay { process.destroyForcibly() })
      val hasClosed = Stream.awakeEvery[F](200.milliseconds).map(_ => !process.isAlive)

      forceClose interruptWhen hasClosed
    } >> {
      val exitValue = process.exitValue()
      if (exitValue == 0) Stream.empty
      else Stream.raiseError(new ChromeProcessException.Exit(exitValue))
    } onFinalize {
      F.delay {
        tempUserDataDir.foreach(FileHelper.deleteDirectory)
      }
    }
    s.compile.drain
  }

}
