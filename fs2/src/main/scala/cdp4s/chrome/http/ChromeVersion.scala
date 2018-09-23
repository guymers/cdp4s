package cdp4s.chrome.http

import cdp4s.ws.WsUri
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeVersion(
  Browser: String,
  `Protocol-Version`: String,
  `User-Agent`: String,
  `V8-Version`: String,
  `WebKit-Version`: String,
  webSocketDebuggerUrl: WsUri
)

object ChromeVersion {
  implicit val decoder: Decoder[ChromeVersion] = deriveDecoder
}
