package cdp4s.chrome

import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration

import cats.syntax.show._
import cdp4s.exception.CDP4sException
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import sttp.model.StatusCode

sealed abstract class ChromeException(msg: String) extends CDP4sException(msg)

sealed abstract class ChromeProcessException(msg: String) extends CDP4sException(msg)

object ChromeProcessException {

  final class NonExecutablePath(path: Path) extends ChromeProcessException(
    show"Chrome path ${path.toFile.getAbsolutePath} is not executable"
  )

  final class Timeout(timeout: FiniteDuration) extends ChromeProcessException(
    show"Failed to connect to chrome instance after ${timeout.toSeconds} seconds"
  )

  final class InvalidDevToolsWebSocketUrl(url: String, reason: Option[String]) extends ChromeProcessException(
    show"Invalid DevTools websocket url $url ${reason.fold("")(s => show": $s")}"
  )

  final class Exit(exitValue: Int) extends ChromeProcessException(
    show"Chrome exited with non-zero exist code: $exitValue"
  )
}

sealed abstract class ChromeHttpException(msg: String) extends CDP4sException(msg)

object ChromeHttpException {

  final class WebSocketConnection(status: StatusCode, body: String) extends ChromeException(
    show"Failed to connect to websocket ${status.code}: ${body.take(250)}"
  )

  final class WebSocketFrameDecoding(data: String, failure: io.circe.Error) extends ChromeException(
    show"Invalid websocket frame $failure: ${data.take(250)}"
  )

  final class InvalidResponse(json: Json) extends ChromeException(
    show"Chrome response has neither an error or a response: ${json.noSpaces.take(250)}"
  )

  final class EventDecoding(json: JsonObject, failure: DecodingFailure) extends ChromeException(
    show"Invalid event $failure: ${json.asJson.noSpaces.take(250)}"
  )

  final class ResponseDecoding(json: JsonObject, failure: DecodingFailure) extends ChromeException(
    show"Invalid response $failure: ${json.asJson.noSpaces.take(250)}"
  )

  final class Error(error: ChromeError) extends ChromeException({
    val errorData = error.data.map(data => show" : $data").getOrElse("")
    show"${error.message}$errorData"
  })

}
