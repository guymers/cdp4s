package protocol.template
package types

import protocol.chrome.ChromeProtocolDomain
import protocol.util.StringUtils

object TypesTemplate {

  def create(domain: ChromeProtocolDomain): TypesTemplate = {

    val types = domain.types.getOrElse(Seq.empty)
    val typeTemplates = types.map(TypeTemplate.create)

    val params = domain.commands.flatMap(_.parameters).flatten
    val paramEnumTemplates = EnumTemplate.extractTemplates(params)

    val resultTemplates = domain.commands.flatMap { command =>
      command.returns match {
        case Some(returns) if returns.length > 1 => Seq {
          ObjectTemplate.create(s"${command.name.capitalize}Result", objExtends = None, returns)
        }
        case _ => Seq.empty
      }
    }

    TypesTemplate(domain.domain, typeTemplates, paramEnumTemplates, resultTemplates)
  }

}

final case class TypesTemplate(
  domain: String,
  typeTemplates: Seq[TypeTemplate],
  paramEnumTemplates: Seq[EnumTemplate],
  resultTemplates: Seq[ObjectTemplate],
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val safeDomain = escapeScalaVariable(domain)

    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.domain.model"),
      Seq(""),
      Seq(s"object $safeDomain {"),
      Seq(
        typeTemplates.flatMap(_.toLines),
        Seq(""),
        Seq("object params {"),
        paramEnumTemplates.flatMap(_.toLines).indent(1),
        Seq("}"),
        Seq(""),
        Seq("object results {"),
        resultTemplates.flatMap(_.toLines).indent(1),
        Seq("}"),
      ).flatten.indent(1),
      Seq("}"),
    ).flatten
  }

}
