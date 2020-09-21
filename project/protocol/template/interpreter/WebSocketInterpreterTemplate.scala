package protocol.template
package interpreter

import protocol.chrome.ChromeProtocolDomain

object WebSocketInterpreterTemplate {

  def create(domains: Vector[ChromeProtocolDomain]): WebSocketInterpreterTemplate = {
    val handlerTemplates = domains.map(InterpreterTemplate.create)
    WebSocketInterpreterTemplate(handlerTemplates)
  }
}

final case class WebSocketInterpreterTemplate(
  handlerTemplates: Vector[InterpreterTemplate],
) {

  def toLines: Lines = {
    Lines(
      Line("/** Generated from Chromium /json/protocol */"),
      Line(""),
      Line("package cdp4s.interpreter"),
      Line(""),
      Line("import io.circe.Decoder"),
      Line("import io.circe.JsonObject"),
      Line("import io.circe.syntax._"),
      Line(""),
      Line("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
      Line("trait WebSocketInterpreter[M[_]] extends cdp4s.domain.All[M] {"),
      Line(""),
      handlerTemplates.flatMap(t => t.toLines :+ "").indent(1),
      Line(""),
      Line("def runCommand[T : Decoder](method: String, params: JsonObject): M[T]").indent(1),
      Line(""),
      Line("}"),
    )
  }
}
