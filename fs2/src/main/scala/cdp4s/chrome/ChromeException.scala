package cdp4s.chrome

import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration

import cats.instances.int._
import cats.instances.long._
import cats.instances.string._
import cats.syntax.show._
import cdp4s.exception.CDP4sException
import io.circe.DecodingFailure
import io.circe.Json
import spinoco.protocol.http.HttpResponseHeader

sealed abstract class ChromeException(msg: String) extends CDP4sException(msg)

sealed abstract class ChromeProcessException(msg: String) extends CDP4sException(msg)

object ChromeProcessException {

  final case class NonExecutablePath(path: Path) extends ChromeProcessException(
    show"Chrome path ${path.toFile.getAbsolutePath} is not executable"
  )

  final case class Timeout(timeout: FiniteDuration) extends ChromeProcessException(
    show"Failed to connect to chrome instance after ${timeout.toSeconds} seconds"
  )

  final case class InvalidDevToolsWebSocketUrl(url: String, reason: Option[String]) extends ChromeProcessException(
    show"Invalid DevTools websocket url $url ${reason.fold("")(s => show": $s")}"
  )

  final case class Exit(exitValue: Int) extends ChromeProcessException(
    show"Chrome exited with non-zero exist code: $exitValue"
  )
}

sealed abstract class ChromeHttpException(msg: String) extends CDP4sException(msg)

object ChromeHttpException {

  final case class InvalidResponse(json: Json) extends ChromeException(
    show"Chrome response has neither an error or a response: ${json.noSpaces.take(250)}"
  )

  final case class EventDecoding(json: Json, failure: DecodingFailure) extends ChromeException(
    show"Invalid event ${failure.getMessage()}: ${json.noSpaces.take(250)}"
  )

  final case class ResponseDecoding(json: Json, failure: DecodingFailure) extends ChromeException(
    show"Invalid response ${failure.getMessage()}: ${json.noSpaces.take(250)}"
  )

  final case class WebSocketConnection(response: HttpResponseHeader) extends ChromeException(
    show"Failed to connect to websocket ${response.status.code} ${response.reason}"
  )

  final case class Error(error: ChromeError) extends ChromeException({
    val errorData = error.data.map(data => show" : $data").getOrElse("")
    show"${error.message}$errorData"
  })

}
