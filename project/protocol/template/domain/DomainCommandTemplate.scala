package protocol.template
package domain

import protocol.chrome.ChromeProtocolCommand
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object DomainCommandTemplate {

  def create(command: ChromeProtocolCommand): DomainCommandTemplate = {
    val parameters = command.parameters.getOrElse(Seq.empty)
    val parameterTemplates = parameters.map(ParameterTemplate.create)

    DomainCommandTemplate(
      command.name,
      command.description,
      parameterTemplates,
      "FS",
      DomainCommandReturnType.create(command)
    )
  }
}

final case class DomainCommandTemplate(
  name: String,
  description: Option[String],
  parameterTemplates: Seq[ParameterTemplate],
  returnTypeConstructor: String,
  returnType: ScalaChromeType,
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val safeName = escapeScalaVariable(name)
    val parameterCtx = ScalaChromeTypeContext.parameterCtx(ctx)
    val returnTypeCtx = ScalaChromeTypeContext.resultCtx(ctx)
    val returnTypeStr = ScalaChromeType.toTypeString(returnType)(returnTypeCtx)

    Seq(
      Some(""),
      description.map(desc => s"/** $desc */")
    ).flatten ++ (if (parameterTemplates.isEmpty) {
      Seq(s"def $safeName: $returnTypeConstructor[$returnTypeStr]")
    } else Seq(
      Seq(s"def $safeName("),
      parameterTemplates.zipWithNext.flatMap { case (parameterTemplate, next) =>
        parameterTemplate.toLines(parameterCtx) ++ (if (next.isDefined) Seq(",") else Seq.empty)
      }.indent(1),
      Seq(s"): $returnTypeConstructor[$returnTypeStr]"),
    ).flatten)
  }
}
