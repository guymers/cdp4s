package cdp4s.ws

import cats.syntax.show._
import io.circe.Decoder
import sttp.model.Uri

sealed abstract case class WsUri(uri: Uri)

object WsUri {

  def fromStr(uri: String): Either[String, WsUri] = {
    Uri.parse(uri).flatMap(fromUri)
  }

  def fromUri(uri: Uri): Either[String, WsUri] = {
    uri.scheme match {
      case "ws" | "wss" => Right(new WsUri(uri) {})
      case scheme => Left(show"Uri scheme $scheme is not a websocket")
    }
  }

  implicit val decoder: Decoder[WsUri] = Decoder[String].emap(fromStr)
}
