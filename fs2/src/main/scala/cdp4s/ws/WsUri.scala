package cdp4s.ws

import cats.syntax.either._
import cats.syntax.show._
import io.circe.Decoder

import java.net.URI
import java.net.URISyntaxException

sealed abstract case class WsUri(value: URI)

object WsUri {

  def fromStr(uri: String): Either[String, WsUri] = {
    (try {
      new URI(uri).asRight
    } catch {
      case e: URISyntaxException => e.getMessage.asLeft
    }).flatMap(fromUri)
  }

  def fromUri(uri: URI): Either[String, WsUri] = {
    Option(uri.getScheme) match {
      case Some("ws") | Some("wss") => Right(new WsUri(uri) {})
      case Some(scheme) => Left(show"URI scheme $scheme is not a websocket")
      case None => Left(show"URI has no scheme")
    }
  }

  implicit val decoder: Decoder[WsUri] = Decoder[String].emap(fromStr)
}
