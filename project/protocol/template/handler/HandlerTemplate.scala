package protocol.template
package handler

import protocol.chrome.ChromeProtocolCommand
import protocol.chrome.ChromeProtocolDomain
import protocol.template.domain.DomainCommandTemplate
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object HandlerTemplate {

  def create(domain: ChromeProtocolDomain): HandlerTemplate = {
    HandlerTemplate(domain.domain, domain.commands)
  }
}

final case class HandlerTemplate(
  domain: String,
  commands: Seq[ChromeProtocolCommand],
) {
  import StringUtils.escapeScalaVariable

  private implicit val ctx: ScalaChromeTypeContext = ScalaChromeTypeContext.defaultCtx(domain)

  def toLines: Seq[String] = {
    val safeDomain = escapeScalaVariable(domain)

    Seq(
      Seq(s"implicit val ${safeDomain}Handler: $safeDomain.Handler[M] = new $safeDomain.Handler[M] {"),
      commands.flatMap(commandTemplate).indent(1),
      Seq("}"),
    ).flatten
  }

  private def commandTemplate(command: ChromeProtocolCommand): Seq[String] = {
    val template = DomainCommandTemplate.create(command).copy(returnTypeConstructor = "M")
    val returnTypeCtx = ScalaChromeTypeContext.resultCtx(ctx)
    val returnTypeStr = ScalaChromeType.toTypeString(template.returnType)(returnTypeCtx)

    val parameters = command.parameters.getOrElse(Seq.empty)
    val params = if (parameters.isEmpty) {
      Seq("val params = JsonObject.empty")
    } else {
      Seq(
        Seq("val params = Map("),
        parameters.zipWithNext.map { case (parameter, next) =>
          val comma = if (next.isDefined) "," else ""
          val paramName = escapeScalaVariable(parameter.name)

          s""""${parameter.name}" -> $paramName.asJson""" + comma
        }.indent(1),
        Seq(").asJsonObject"),
      ).flatten
    }

    // define a custom decoder if there is only one returned value
    val returns = command.returns.getOrElse(Seq.empty)
    val (customDecoder, customDecoderLines) = returns match {
      case ret +: Seq() =>
        val lines = Seq(
          "val decoder = io.circe.Decoder.instance { c =>",
          s"""  c.downField("${escapeScalaVariable(ret.name)}").as[$returnTypeStr]""",
          "}",
          "",
        )
        ("(decoder)", lines)

      case _ =>
        ("", Seq.empty)
    }

    template.toLines ++ Seq("= {") ++ Seq(
      Seq(
        params,
        Seq(""),
        customDecoderLines,
        Seq(s"""runCommand[$returnTypeStr]("$domain.${command.name}", params)""" + customDecoder),
      ).flatten.indent(1),
      Seq("}")
    ).flatten
  }
}

