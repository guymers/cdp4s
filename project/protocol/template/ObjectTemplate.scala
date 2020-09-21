package protocol.template

import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.template.types.ScalaChromeTypeContext

object ObjectTemplate {

  def create(
    name: String,
    description: Option[String],
    objExtends: Option[String],
    properties: Vector[ChromeProtocolTypeDefinition],
  ): ObjectTemplate = ObjectTemplate(
    name,
    description,
    objExtends,
    properties.map(ParameterTemplate.create),
    EnumTemplate.extractTemplates(properties),
  )

}

final case class ObjectTemplate(
  name: String,
  description: Option[String],
  `extends`: Option[String],
  parameterTemplates: Vector[ParameterTemplate],
  enumTemplates: Vector[EnumTemplate],
) {
  import protocol.util.StringUtils.escapeScalaVariable

  private val safeName = escapeScalaVariable(name)

  def toLines(implicit ctx: ScalaChromeTypeContext): Vector[String] = {

    if (parameterTemplates.isEmpty) {
      Lines(
        descriptionToLines(description, parameterTemplates.flatMap(_.scalaDocParam)),
        Line(s"object $safeName" + `extends`.map(e => s" extends ${escapeScalaVariable(e)}").getOrElse("") + " {"),
        enumTemplates.flatMap(_.toLines).indent(1),
        Vector(
          s"",
          s"implicit val decoder: io.circe.Decoder[$safeName.type] = {",
          s"  io.circe.Decoder.decodeUnit.map((_: Unit) => $safeName)",
          s"}",
          s"implicit val encoder: io.circe.Encoder.AsObject[$safeName.type] = {",
          s"  io.circe.Encoder.AsObject.instance(_ => io.circe.JsonObject.empty)",
          s"}",
        ).indent(1),
        Line("}"),
        Line(""),
      )
    } else {
      val propertyCtx = ScalaChromeTypeContext.propertyCtx(ctx, name)
      val props = parameterTemplates.map { parameterTemplate =>
        parameterTemplate.parameter(propertyCtx) concat ","
      }

      Lines(
        descriptionToLines(description, parameterTemplates.flatMap(_.scalaDocParam)),
        Line("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
        Line(s"final case class $safeName("),
        props.indent(1),
        Line(")" + `extends`.map(e => s" extends ${escapeScalaVariable(e)}").getOrElse("")),
        Line(""),
        Line(s"object $safeName {"),
        enumTemplates.flatMap(_.toLines).indent(1),
        Vector(
          "",
          s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.generic.semiauto.deriveDecoder",
          s"implicit val encoder: io.circe.Encoder.AsObject[$safeName] = io.circe.generic.semiauto.deriveEncoder",
        ).indent(1),
        Line("}"),
        Line(""),
      )
    }
  }
}
