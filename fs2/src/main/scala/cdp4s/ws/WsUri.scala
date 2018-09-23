package cdp4s.ws

import cats.instances.string._
import cats.syntax.either._
import cats.syntax.show._
import io.circe.Decoder
import scodec.Attempt
import scodec.Err
import spinoco.fs2.http.websocket.WebSocketRequest
import spinoco.protocol.http.HostPort
import spinoco.protocol.http.HttpScheme
import spinoco.protocol.http.Uri
import spinoco.protocol.http.Uri.QueryParameter

final case class WsUri(
  secure: Boolean,
  host: HostPort,
  path: Uri.Path,
  query: Uri.Query
) {
  def wsRequest: WebSocketRequest = {
    def create(host: String, path: String, params: List[QueryParameter]): WebSocketRequest = if (secure) {
      WebSocketRequest.wss(host, path, params: _*)
    } else {
      WebSocketRequest.ws(host, path, params: _*)
    }

    create("localhost", path.stringify, query.params).copy(hostPort = host)
  }

  override def toString: String = {
    val schema = if (secure) HttpScheme.WSS else HttpScheme.WS
    val uri = Uri(schema, host, path, query)
    val uriStrOpt = for {
      encodedUri <- Uri.codec.encode(uri).toOption
      str <- encodedUri.decodeUtf8.toOption
    } yield str
    val uriStr = uriStrOpt.getOrElse("???")
    show"WsUri($uriStr)"
  }
}

object WsUri {

  def fromUri(uri: Uri): Attempt[WsUri] = {
    uri.scheme match {
      case HttpScheme.WS => Attempt.successful {
        WsUri(secure = false, uri.host, uri.path, uri.query)
      }
      case HttpScheme.WSS => Attempt.successful {
        WsUri(secure = true, uri.host, uri.path, uri.query)
      }
      case scheme => Attempt.failure {
        Err(show"Uri scheme ${scheme.tpe} is not a websocket")
      }
    }
  }

  implicit val decoder: Decoder[WsUri] = Decoder[String].emap { str =>
    Uri.parse(str).flatMap(fromUri).toEither.leftMap(_.messageWithContext)
  }
}
