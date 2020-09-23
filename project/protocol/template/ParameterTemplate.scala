package protocol.template

import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.chrome.Deprecated
import protocol.chrome.Experimental
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.template.types.ScalaChromeTypeWrapper
import protocol.util.StringUtils

object ParameterTemplate {

  def create(typeDef: ChromeProtocolTypeDefinition): ParameterTemplate = ParameterTemplate(
    name = typeDef.name,
    description = typeDef.description,
    deprecated = typeDef.deprecated,
    experimental = typeDef.experimental,
    tpe = ScalaChromeType.chromeTypeToScala(typeDef.name.capitalize, typeDef.`type`),
  )
}

final case class ParameterTemplate(
  name: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  tpe: ScalaChromeType,
) {
  import StringUtils.escapeScalaVariable

  val variableName: String = escapeScalaVariable(name)

  def scalaDocParam: Option[String] = {
    description.map(desc => s"@param $variableName $desc")
  }

  def parameter(implicit ctx: ScalaChromeTypeContext): String = {
    val annotations = {
      val lines = annotationsToLines(deprecated, experimental)
      if (lines.nonEmpty) lines.mkString(" ") + " " else ""
    }
    val isOptional = tpe.wrappers.headOption.collect {
      case ScalaChromeTypeWrapper.Optional => true
    }.getOrElse(false)
    val defaultValue = if (isOptional) " = scala.None" else ""

    s"$annotations$variableName: ${ScalaChromeType.toTypeString(tpe)}$defaultValue"
  }
}
