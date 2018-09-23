package cdp4s.chrome

import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration

import cats.Show
import cats.instances.int._
import cats.instances.long._
import cats.instances.string._
import cats.syntax.show._
import cdp4s.chrome.http.ChromeTab
import cdp4s.domain.model.Target
import cdp4s.exception.CDP4sException
import io.circe.DecodingFailure
import io.circe.Json
import spinoco.protocol.http.HttpResponseHeader
import spinoco.protocol.mime.ContentType

sealed abstract class ChromeException(msg: String) extends CDP4sException(msg)

sealed abstract class ChromeProcessException(msg: String) extends CDP4sException(msg)

object ChromeProcessException {

  final case class NonExecutablePath(path: Path) extends RuntimeException(
    show"Chrome path ${path.toFile.getAbsolutePath} is not executable"
  )

  final case class Timeout(timeout: FiniteDuration) extends ChromeProcessException(
    show"Failed to connect to chrome instance after ${timeout.toSeconds} seconds"
  )

  final case class Exit(exitValue: Int) extends ChromeProcessException(
    show"Chrome exited with non-zero exist code: $exitValue"
  )
}

sealed abstract class ChromeHttpException(msg: String) extends CDP4sException(msg)

object ChromeHttpException {

  final case class UnsuccessfulResponse(header: HttpResponseHeader, bodyFragment: String) extends ChromeHttpException(
    s"Invalid Chrome http client response (${header.status.code}): ${bodyFragment.take(256)}"
  )

  final case class ClientResponseException(err: io.circe.Error, body: String) extends ChromeHttpException(
    s"Invalid Chrome http client response: $err - $body"
  )

  final case class NonJsonContentType(contentType: Option[ContentType]) extends ChromeHttpException(
    s"Response returned invalid content type ${contentType.map(_.show).getOrElse("<none>")}"
  )

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

  final case class NoWebSocketDebuggerUrl(tab: ChromeTab) extends ChromeException(
    show"Chrome tab has no web socket debugger url: ${tab.title} (${tab.url})"
  )

  final case class NoTab(targetId: Target.TargetID) extends ChromeException(
    show"Cannot find tab with target id ${targetId.value}"
  )

  private implicit val showContentType: Show[ContentType] = Show.show { c =>
    show"${c.mediaType.mainType}/${c.mediaType.subType}"
  }
}
