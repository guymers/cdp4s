package protocol.template
package types

import protocol.chrome.ChromeProtocolTypeDescription
import protocol.util.StringUtils

object NewTypeTemplate {

  def create(typeDesc: ChromeProtocolTypeDescription): NewTypeTemplate = {
    val tpe = ScalaChromeType.chromeTypeToScala(typeDesc.id, typeDesc.`type`)

    NewTypeTemplate(typeDesc.id, tpe)
  }
}

final case class NewTypeTemplate(
  name: String,
  tpe: ScalaChromeType
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val safeName = escapeScalaVariable(name)
    val typeStr = ScalaChromeType.toTypeString(tpe)

    Seq(
      Seq(s"final case class $safeName(value: $typeStr) extends AnyVal"),
      Seq(""),
      Seq(s"object $safeName {"),
      Seq(
        "import io.circe.syntax._",
        "",
        s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.Decoder[$typeStr].map($safeName.apply)",
        s"implicit val encoder: io.circe.Encoder[$safeName] = io.circe.Encoder.instance(_.value.asJson)",
      ).indent(1),
      Seq("}"),
    ).flatten
  }
}
