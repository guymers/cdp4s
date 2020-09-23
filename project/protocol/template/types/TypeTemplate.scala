package protocol.template
package types

import cats.data.NonEmptyVector
import protocol.chrome.ChromeProtocolType
import protocol.chrome.ChromeProtocolTypeDescription

sealed trait TypeTemplate {
  def toLines(implicit ctx: ScalaChromeTypeContext): Lines
}
object TypeTemplate {

  final case class NewType(template: NewTypeTemplate) extends TypeTemplate {
    def toLines(implicit ctx: ScalaChromeTypeContext): Lines = template.toLines
  }
  final case class Obj(template: ObjectTemplate) extends TypeTemplate {
    def toLines(implicit ctx: ScalaChromeTypeContext): Lines = template.toLines
  }
  final case class Enum(template: EnumTemplate) extends TypeTemplate {
    def toLines(implicit ctx: ScalaChromeTypeContext): Lines = template.toLines
  }

  def create(typeDesc: ChromeProtocolTypeDescription): Option[TypeTemplate] = {
    import ChromeProtocolType._

    typeDesc.`type` match {
      case any(_) | binary(_) | string(_) | integer(_) | number(_) | boolean(_) | array(_, _) => Some {
        NewType { NewTypeTemplate.create(typeDesc) }
      }

      case obj(properties, _) => Some {
        NonEmptyVector.fromVector(properties) match {
          case None => NewType { NewTypeTemplate.create(typeDesc) }
          case Some(props) => Obj { ObjectTemplate.create(
            name = typeDesc.id,
            description = typeDesc.description,
            deprecated = typeDesc.deprecated,
            experimental = typeDesc.experimental,
            objExtends = None,
            properties = props.toVector,
          ) }
        }
      }

      case enum(values, _) => Some {
        Enum { EnumTemplate(typeDesc.id, typeDesc.description, values) }
      }

      case reference(_, _, _) =>
        None
    }
  }
}
