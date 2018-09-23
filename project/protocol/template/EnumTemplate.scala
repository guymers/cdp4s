package protocol.template

import cats.instances.vector._
import cats.kernel.instances.map._
import cats.kernel.instances.set._
import cats.syntax.foldable._
import protocol.chrome.ChromeProtocolType
import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.util.StringUtils

object EnumTemplate {

  def extractTemplates(typeDefs: Seq[ChromeProtocolTypeDefinition]): Vector[EnumTemplate] = {
    import ChromeProtocolType._

    typeDefs
      .collect {
        case ChromeProtocolTypeDefinition(name, _, enum(values, _)) => Map(name -> values)
        case ChromeProtocolTypeDefinition(name, _, array(enum(values, _), _)) => Map(name -> values)
      }
      .toVector
      .combineAll
      .toVector
      .sortBy(_._1)
      .map { case (name, values) =>
        EnumTemplate(name.capitalize, values)
      }
  }

}

final case class EnumTemplate(
  name: String,
  values: Set[String],
) {
  import StringUtils.escapeScalaVariable

  def toLines: Seq[String] = {
    val safeName = escapeScalaVariable(name)
    val itemNames = values.toVector.sorted

    Seq(
      Seq(s"sealed abstract class $safeName(val value: scala.Predef.String)"),
      Seq(s"object $safeName {"),
      (
        itemNames.map { itemName =>
          s"""case object ${escapeScalaVariable(itemName)} extends $safeName("$itemName")"""
        } ++ Seq(
          Seq(""),
          Seq(s"implicit val encoder: io.circe.Encoder[$safeName] = io.circe.Encoder.encodeString.contramap(_.value)"),
          Seq(s"implicit val decoder: io.circe.Decoder[$safeName] = io.circe.Decoder.decodeString.emap {"),
          itemNames.map { itemName =>
            s"case ${escapeScalaVariable(itemName)}.value => scala.util.Right(${escapeScalaVariable(itemName)})"
          }.indent(1),
          Seq(s"""case str => scala.util.Left(s"Invalid value $$str for enum $safeName")""").indent(1),
          Seq("}"),
        ).flatten
        ).indent(1),
      Seq("}"),
    ).flatten
  }
}
