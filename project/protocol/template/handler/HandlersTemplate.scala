package protocol.template
package handler

import protocol.chrome.ChromeProtocolDomain

object HandlersTemplate {

  def create(domains: Seq[ChromeProtocolDomain]): HandlersTemplate = {
    val handlerTemplates = domains.map(HandlerTemplate.create)
    HandlersTemplate(handlerTemplates)
  }
}

final case class HandlersTemplate(
  handlerTemplates: Seq[HandlerTemplate],
) {

  def toLines: Seq[String] = {
    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.domain"),
      Seq(""),
      Seq("import io.circe.JsonObject"),
      Seq("import io.circe.syntax._"),
      Seq(""),
      Seq("trait ChromeWebSocketHandlers[M[_]] {"),
      Seq(""),
      handlerTemplates.flatMap(t => t.toLines :+ "").indent(1),
      Seq(""),
      Seq("def runCommand[T : io.circe.Decoder](method: String, params: JsonObject): M[T]").indent(1),
      Seq(""),
      Seq("}"),
    ).flatten
  }
}
