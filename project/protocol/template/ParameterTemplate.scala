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

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val isOptional = tpe.wrappers.headOption.collect {
      case ScalaChromeTypeWrapper.Optional => true
    }.getOrElse(false)
    None
    val defaultValue = if (isOptional) s" = scala.None" else ""

    description.map(desc => s"/** $desc */").toSeq :+
      s"${escapeScalaVariable(name)}: ${ScalaChromeType.toTypeString(tpe)}$defaultValue"
  }
}
