package protocol.template

import cats.instances.vector._
import cats.kernel.instances.map._
import cats.kernel.instances.set._
import cats.syntax.foldable._
import protocol.chrome.ChromeProtocolType
import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.util.StringUtils

object EnumTemplate {

  def extractTemplates(typeDefs: Vector[ChromeProtocolTypeDefinition]): Vector[EnumTemplate] = {
    import ChromeProtocolType._

    typeDefs
      .collect {
        case ChromeProtocolTypeDefinition(name, _, _, _, enum(values, _)) => Map(name -> values)
        case ChromeProtocolTypeDefinition(name, _, _, _, array(enum(values, _), _)) => Map(name -> values)
      }
      .combineAll
      .toVector
      .sortBy(_._1)
      .map { case (name, values) =>
        EnumTemplate(name.capitalize, description = None, values)
      }
  }

}

final case class EnumTemplate(
  name: String,
  description: Option[String],
  values: Set[String],
) {
  import StringUtils.escapeScalaVariable

  def toLines: Lines = {
    val safeName = escapeScalaVariable(name)
    val itemNames = values.toVector.sorted

    Lines(
      descriptionToLines(description, Vector.empty),
      Line(s"sealed abstract class $safeName(val value: scala.Predef.String) extends Product with Serializable"),
      Line(s"object $safeName {"),
      (
        itemNames.map { itemName =>
          s"""case object ${escapeScalaVariable(itemName)} extends $safeName("$itemName")"""
        } ++ Lines(
          Line(""),
          Line(s"implicit val encoder: io.circe.Encoder[$safeName] = io.circe.Encoder.encodeString.contramap(_.value)"),
          Line(s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.Decoder.decodeString.emap {"),
          itemNames.map { itemName =>
            s"case ${escapeScalaVariable(itemName)}.value => scala.util.Right(${escapeScalaVariable(itemName)})"
          }.indent(1),
          Line(s"""case str => scala.util.Left(s"Invalid value $$str for enum $safeName")""").indent(1),
          Line("}"),
        )
      ).indent(1),
      Line("}"),
    )
  }
}
