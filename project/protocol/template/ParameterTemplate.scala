package protocol.template

import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.template.types.ScalaChromeType
import protocol.template.types.ScalaChromeTypeContext
import protocol.template.types.ScalaChromeTypeWrapper
import protocol.util.StringUtils

object ParameterTemplate {

  def create(typeDef: ChromeProtocolTypeDefinition): ParameterTemplate = {
    val tpe = ScalaChromeType.chromeTypeToScala(typeDef.name.capitalize, typeDef.`type`)
    ParameterTemplate(typeDef.name, typeDef.description, tpe)
  }
}

final case class ParameterTemplate(
  name: String,
  description: Option[String],
  tpe: ScalaChromeType,
) {
  import StringUtils.escapeScalaVariable

  val variableName: String = escapeScalaVariable(name)

  def scalaDocParam: Option[String] = {
    description.map(desc => s"@param $variableName $desc")
  }

  def parameter(implicit ctx: ScalaChromeTypeContext): String = {
    val isOptional = tpe.wrappers.headOption.collect {
      case ScalaChromeTypeWrapper.Optional => true
    }.getOrElse(false)
    val defaultValue = if (isOptional) " = scala.None" else ""

    s"$variableName: ${ScalaChromeType.toTypeString(tpe)}$defaultValue"
  }
}
