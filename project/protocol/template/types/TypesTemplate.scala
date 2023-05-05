package protocol.template
package types

import protocol.chrome.ChromeProtocolDomain
import protocol.util.StringUtils

object TypesTemplate {

  def create(domain: ChromeProtocolDomain): TypesTemplate = {

    val types = domain.types.map(_.toVector).getOrElse(Vector.empty)
    val typeTemplates = types.flatMap(TypeTemplate.create)

    val params = domain.commands.toVector.flatMap(_.parameters.map(_.toVector).getOrElse(Vector.empty))
    val paramEnumTemplates = EnumTemplate.extractTemplates(params)

    val resultTemplates = domain.commands.toVector.flatMap { command =>
      command.returns match {
        case Some(returns) if returns.length > 1 => Vector {
          ObjectTemplate.create(
            name = s"${command.name.capitalize}Result",
            description = command.description,
            deprecated = command.deprecated,
            experimental = command.experimental,
            objExtends = None,
            properties = returns.toVector,
          )
        }
        case _ => Vector.empty
      }
    }

    TypesTemplate(domain.domain, typeTemplates, paramEnumTemplates, resultTemplates)
  }

}

final case class TypesTemplate(
  domain: String,
  typeTemplates: Vector[TypeTemplate],
  paramEnumTemplates: Vector[EnumTemplate],
  resultTemplates: Vector[ObjectTemplate],
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Lines = {
    val safeDomain = escapeScalaVariable(domain)

    Lines(
      Line("/** Generated from Chromium /json/protocol */"),
      Line(""),
      Line("package cdp4s.domain.model"),
      Line(""),
      Line(s"object $safeDomain {"),
      Lines(
        typeTemplates.flatMap(_.toLines),
        Line(""),
        Line("object params {"),
        paramEnumTemplates.flatMap(_.toLines).indent(1),
        Line("}"),
        Line(""),
        Line("object results {"), {
          val _ctx = ScalaChromeTypeContext.resultsCtx(ctx)
          resultTemplates.flatMap(_.toLines(_ctx)).indent(1)
        },
        Line("}"),
      ).indent(1),
      Line("}"),
    )
  }

}
