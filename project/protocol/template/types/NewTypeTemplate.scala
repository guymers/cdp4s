package protocol.template
package types

import protocol.chrome.ChromeProtocolTypeDescription
import protocol.chrome.Deprecated
import protocol.chrome.Experimental
import protocol.util.StringUtils

object NewTypeTemplate {

  def create(typeDesc: ChromeProtocolTypeDescription): NewTypeTemplate = NewTypeTemplate(
    name = typeDesc.id,
    description = typeDesc.description,
    deprecated = typeDesc.deprecated,
    experimental = typeDesc.experimental,
    tpe = ScalaChromeType.chromeTypeToScala(typeDesc.id, typeDesc.`type`),
  )
}

final case class NewTypeTemplate(
  name: String,
  description: Option[String],
  deprecated: Deprecated,
  experimental: Experimental,
  tpe: ScalaChromeType,
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Lines = {
    val safeName = escapeScalaVariable(name)
    val typeStr = ScalaChromeType.toTypeString(tpe)

    descriptionToLines(description, Vector.empty) ++
    annotationsToLines(deprecated, experimental) ++
    Lines(
      Line(s"final case class $safeName(value: $typeStr) extends AnyVal"),
      Line(""),
      Line(s"object $safeName {"),
      Vector(
        "import _root_.io.circe.syntax.EncoderOps",
        "",
        s"implicit val decoder: _root_.io.circe.Decoder[$safeName] = _root_.io.circe.Decoder[$typeStr].map($safeName.apply)",
        s"implicit val encoder: _root_.io.circe.Encoder[$safeName] = _root_.io.circe.Encoder.instance(_.value.asJson)",
      ).indent(1),
      Line("}"),
    )
  }
}
