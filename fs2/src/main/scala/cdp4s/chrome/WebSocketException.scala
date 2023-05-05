package cdp4s.chrome

import cats.syntax.show.*
import cdp4s.exception.CDP4sException
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.*

sealed abstract class WebSocketException(msg: String) extends CDP4sException(msg)

object WebSocketException {

  final class Connection(code: Int, body: String) extends WebSocketException(
      show"Failed to connect to websocket $code: ${body.take(250)}",
    )

  final class Timeout(reason: String) extends WebSocketException(
      show"Timed out when communicating with websocket: ${reason.take(250)}",
    )

  final class FrameDecoding(data: String, failure: io.circe.Error) extends WebSocketException(
      show"Invalid websocket frame $failure: ${data.take(250)}",
    )

  final class InvalidResponse(json: Json) extends WebSocketException(
      show"Chrome response has neither an error or a response: ${json.noSpaces.take(250)}",
    )

  final class NoCallback(id: Long, json: JsonObject) extends WebSocketException(
      show"No callback found for id $id: ${json.asJson.noSpaces.take(250)}",
    )

  // TODO this error (all errors?) occurring hangs the program
  final class EventDecoding(json: JsonObject, failure: DecodingFailure) extends WebSocketException(
      show"Invalid event $failure: ${json.asJson.noSpaces.take(250)}",
    )

  final class ResponseDecoding(json: JsonObject, failure: DecodingFailure) extends WebSocketException(
      show"Invalid response $failure: ${json.asJson.noSpaces.take(250)}",
    )

  final class ShuttingDown() extends WebSocketException(
      "Websocket is shutting down",
    )

  final class Error(error: ChromeError) extends WebSocketException({
      val errorData = error.data.map(data => show" : $data").getOrElse("")
      show"Chrome returned error ${error.message}$errorData"
    })

}
