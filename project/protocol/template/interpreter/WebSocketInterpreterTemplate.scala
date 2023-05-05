package protocol.template
package interpreter

import protocol.chrome.ChromeProtocolDomain

object WebSocketInterpreterTemplate {

  def create(domains: Vector[ChromeProtocolDomain])(scala3: Boolean): WebSocketInterpreterTemplate = {
    val handlerTemplates = domains.map(InterpreterTemplate.create(_)(scala3))
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
      Line("import _root_.io.circe.syntax.EncoderOps"),
      Line(""),
      Line("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
      Line("trait WebSocketInterpreter[M[_]] extends cdp4s.domain.All[M] {"),
      Line(""),
      handlerTemplates.flatMap(t => t.toLines :+ "").indent(1),
      Line(""),
      Lines(
        Line("def runCommand[T : _root_.io.circe.Decoder]("),
        Line("  method: scala.Predef.String,"),
        Line("  params: _root_.io.circe.JsonObject,"),
        Line("): M[T]"),
      ).indent(1),
      Line(""),
      Line("}"),
    )
  }
}
