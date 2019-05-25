package protocol.template
package interpreter

import protocol.chrome.ChromeProtocolDomain

object WebSocketInterpreterTemplate {

  def create(domains: Seq[ChromeProtocolDomain]): WebSocketInterpreterTemplate = {
    val handlerTemplates = domains.map(InterpreterTemplate.create)
    WebSocketInterpreterTemplate(handlerTemplates)
  }
}

final case class WebSocketInterpreterTemplate(
  handlerTemplates: Seq[InterpreterTemplate],
) {

  def toLines: Seq[String] = {
    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.interpreter"),
      Seq(""),
      Seq("import io.circe.Decoder"),
      Seq("import io.circe.JsonObject"),
      Seq("import io.circe.syntax._"),
      Seq(""),
      Seq("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
      Seq("trait WebSocketInterpreter[M[_]] extends cdp4s.domain.All[M] {"),
      Seq(""),
      handlerTemplates.flatMap(t => t.toLines :+ "").indent(1),
      Seq(""),
      Seq("def runCommand[T : Decoder](method: String, params: JsonObject): M[T]").indent(1),
      Seq(""),
      Seq("}"),
    ).flatten
  }
}
