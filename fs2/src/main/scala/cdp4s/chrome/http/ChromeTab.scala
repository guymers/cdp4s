package cdp4s.chrome.http

import cdp4s.ws.WsUri
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class ChromeTabId(id: String) extends AnyVal

object ChromeTabId {
  implicit val decoder: Decoder[ChromeTabId] = Decoder.decodeString.map(ChromeTabId.apply)
}

final case class ChromeTab(
  id: ChromeTabId,
  `type`: String,
  title: String,
  description: String,
  url: String,
  devtoolsFrontendUrl: Option[String],
  webSocketDebuggerUrl: Option[WsUri] // not present if connection exists
)

object ChromeTab {
  implicit val decoder: Decoder[ChromeTab] = deriveDecoder
}
