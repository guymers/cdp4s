package protocol.template
package domain

import protocol.chrome.ChromeProtocolCommand
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object DomainCommandTemplate {

  def create(command: ChromeProtocolCommand): DomainCommandTemplate = {
    val parameters = command.parameters.map(_.toVector).getOrElse(Vector.empty)
    val parameterTemplates = parameters.map(ParameterTemplate.create)

    DomainCommandTemplate(
      command.name,
      command.description,
      parameterTemplates,
      "F",
      DomainCommandReturnType.create(command)
    )
  }
}

final case class DomainCommandTemplate(
  name: String,
  description: Option[String],
  parameterTemplates: Vector[ParameterTemplate],
  returnTypeConstructor: String,
  returnType: ScalaChromeType,
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Lines = {
    val safeName = escapeScalaVariable(name)
    val parameterCtx = ScalaChromeTypeContext.parameterCtx(ctx)
    val returnTypeCtx = ScalaChromeTypeContext.resultCtx(ctx)
    val returnTypeStr = ScalaChromeType.toTypeString(returnType)(returnTypeCtx)

    val desc = Lines(
      Vector("/**"),
      description.map(desc => Vector(s" * $desc", " *")).getOrElse(Vector.empty),
      parameterTemplates.flatMap { parameterTemplate =>
        parameterTemplate.scalaDocParam.map(v => s" * $v").toVector
      },
      Vector(" */"),
    )

    val method = if (parameterTemplates.isEmpty) {
      Vector(s"def $safeName: $returnTypeConstructor[$returnTypeStr]")
    } else Lines(
      Vector(s"def $safeName("),
      parameterTemplates.map { parameterTemplate =>
        parameterTemplate.parameter(parameterCtx) concat ","
      }.indent(1),
      Vector(s"): $returnTypeConstructor[$returnTypeStr]"),
    )

    Vector("") ++ desc ++ method
  }
}
