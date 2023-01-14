package cdp4s.chrome

import cats.syntax.show.*
import cdp4s.exception.CDP4sException

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

sealed abstract class ProcessException(msg: String) extends CDP4sException(msg)

object ProcessException {

  final class NonExecutablePath(path: Path) extends ProcessException(
      show"Chrome path ${path.toFile.getAbsolutePath} is not executable",
    )

  final class Timeout(timeout: FiniteDuration) extends ProcessException(
      show"Failed to connect to chrome instance after ${timeout.toSeconds} seconds",
    )

  final class InvalidDevToolsWebSocketUrl(url: String, reason: Option[String]) extends ProcessException(
      show"Invalid DevTools websocket url $url ${reason.fold("")(s => show": $s")}",
    )

  final class Exit(exitValue: Int) extends ProcessException(
      show"Chrome exited with non-zero exist code: $exitValue",
    )
}
