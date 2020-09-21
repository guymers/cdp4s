package protocol.template
package types

import protocol.chrome.ChromeProtocolTypeDescription
import protocol.util.StringUtils

object NewTypeTemplate {

  def create(typeDesc: ChromeProtocolTypeDescription): NewTypeTemplate = {
    val tpe = ScalaChromeType.chromeTypeToScala(typeDesc.id, typeDesc.`type`)

    NewTypeTemplate(typeDesc.id, typeDesc.description, tpe)
  }
}

final case class NewTypeTemplate(
  name: String,
  description: Option[String],
  tpe: ScalaChromeType
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Lines = {
    val safeName = escapeScalaVariable(name)
    val typeStr = ScalaChromeType.toTypeString(tpe)

    Lines(
      descriptionToLines(description, Vector.empty),
      Line(s"final case class $safeName(value: $typeStr) extends AnyVal"),
      Line(""),
      Line(s"object $safeName {"),
      Vector(
        "import io.circe.syntax._",
        "",
        s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.Decoder[$typeStr].map($safeName.apply)",
        s"implicit val encoder: io.circe.Encoder[$safeName] = io.circe.Encoder.instance(_.value.asJson)",
      ).indent(1),
      Line("}"),
    )
  }
}
