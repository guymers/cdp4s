package protocol.template
package interpreter

import cats.data.NonEmptyVector
import protocol.chrome.ChromeProtocolCommand
import protocol.chrome.ChromeProtocolDomain
import protocol.template.domain.DomainCommandTemplate
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object InterpreterTemplate {

  def create(domain: ChromeProtocolDomain)(scala3: Boolean): InterpreterTemplate = {
    InterpreterTemplate(domain.domain, domain.commands)(scala3)
  }
}

final case class InterpreterTemplate(
  domain: String,
  commands: NonEmptyVector[ChromeProtocolCommand],
)(scala3: Boolean) {
  import StringUtils.escapeScalaVariable

  private implicit val ctx: ScalaChromeTypeContext = ScalaChromeTypeContext.defaultCtx(domain)

  def toLines: Lines = {
    Lines(
      Line(s"override implicit val ${StringUtils.unCamelCase(domain)}: cdp4s.domain.$domain[M] = new cdp4s.domain.$domain[M] {"),
      commands.toVector.flatMap(commandTemplate).indent(1),
      Line("}"),
    )
  }

  private def commandTemplate(command: ChromeProtocolCommand): Lines = {
    val template = DomainCommandTemplate.create(command).copy(returnTypeConstructor = "M")
    val returnTypeCtx = ScalaChromeTypeContext.resultCtx(ctx)
    val returnTypeStr = ScalaChromeType.toTypeString(template.returnType)(returnTypeCtx)

    val parameters = command.parameters.map(_.toVector).getOrElse(Vector.empty)
    val params = if (parameters.isEmpty) {
      Vector("val params = _root_.io.circe.JsonObject.empty")
    } else {
      Vector("val params = _root_.io.circe.JsonObject(") ++
      parameters.map { parameter =>
        val paramName = escapeScalaVariable(parameter.name)

        s""""${parameter.name}" -> $paramName.asJson,"""
      }.indent(1) ++
      Vector(")"),
    }

    // define a custom decoder if there is only one returned value
    val (customDecoder, customDecoderLines) = command.returns match {
      case Some(NonEmptyVector(ret, tail)) if tail.isEmpty =>
        val lines = Vector(
          "val decoder = _root_.io.circe.Decoder.instance { c =>",
          s"""  c.downField("${escapeScalaVariable(ret.name)}").as[$returnTypeStr]""",
          "}",
          "",
        )
        if (scala3) ("(using decoder)", lines) else ("(decoder)", lines)

      case _ =>
        ("", Vector.empty)
    }

    template.toLines ++ Vector("= {") ++ Lines(
      Vector(
        params,
        Vector(""),
        customDecoderLines,
        Vector(s"""runCommand[$returnTypeStr]("$domain.${command.name}", params)""" + customDecoder),
      ).flatten.indent(1),
      Vector("}")
    )
  }
}

