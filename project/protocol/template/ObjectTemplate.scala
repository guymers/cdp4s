package protocol.template

import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils.escapeScalaVariable

object ObjectTemplate {

  def create(
    name: String,
    objExtends: Option[String],
    properties: Seq[ChromeProtocolTypeDefinition]
  ): ObjectTemplate = {
    ObjectTemplate(
      name,
      objExtends,
      properties.map(ParameterTemplate.create),
      EnumTemplate.extractTemplates(properties)
    )
  }

}

final case class ObjectTemplate(
  name: String,
  xtends: Option[String],
  propertyTemplates: Seq[ParameterTemplate],
  enumTemplates: Seq[EnumTemplate],
) {
  private val safeName = escapeScalaVariable(name)

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val propertyCtx = ScalaChromeTypeContext.propertyCtx(ctx, name)
    val props = propertyTemplates.zipWithNext.flatMap { case (propertyTemplate, next) =>
      val comma = if (next.isDefined) Seq(",") else Seq.empty

      propertyTemplate.toLines(propertyCtx) ++ comma
    }

    Seq(
      Seq(s"final case class $safeName("),
      props.indent(1),
      Seq(")" + xtends.map(e => s" extends ${escapeScalaVariable(e)}").getOrElse("")),
      Seq(""),
      Seq(s"object $safeName {"),
      enumTemplates.flatMap(_.toLines).indent(1),
      Seq(
        "",
        s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.generic.semiauto.deriveDecoder",
        s"implicit val encoder: io.circe.Encoder[$safeName] = io.circe.generic.semiauto.deriveEncoder",
      ).indent(1),
      Seq("}"),
      Seq(""),
    ).flatten
  }
}
