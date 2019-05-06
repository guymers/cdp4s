package protocol.template
package types

import protocol.chrome.ChromeProtocolType
import protocol.chrome.ChromeProtocolTypeDescription

object TypeTemplate {

  def create(typeDesc: ChromeProtocolTypeDescription): TypeTemplate = {
    import ChromeProtocolType._
    import InnerTypeTemplate._

    val template = typeDesc.`type` match {
      case any(_) | binary(_) | string(_) | integer(_) | number(_) | boolean(_) | array(_, _) => Some {
        NewType { NewTypeTemplate.create(typeDesc) }
      }

      case obj(properties, _) => Some {
        Obj { ObjectTemplate.create(typeDesc.id, objExtends = None, properties) }
      }

      case enum(values, _) => Some {
        Enum { EnumTemplate(typeDesc.id, values) }
      }

      case reference(_, _, _) =>
        None
    }

    TypeTemplate(typeDesc.description, template)
  }

}

final case class TypeTemplate(
  description: Option[String],
  template: Option[InnerTypeTemplate],
) {

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val desc = description.map(desc => s"/** $desc */").toSeq

    desc ++ template.toSeq.flatMap {
      case InnerTypeTemplate.NewType(tmpl) => tmpl.toLines
      case InnerTypeTemplate.Obj(tmpl) => tmpl.toLines
      case InnerTypeTemplate.Enum(tmpl) => tmpl.toLines
    }
  }

}

sealed trait InnerTypeTemplate
object InnerTypeTemplate {
  final case class NewType(template: NewTypeTemplate) extends InnerTypeTemplate
  final case class Obj(template: ObjectTemplate) extends InnerTypeTemplate
  final case class Enum(template: EnumTemplate) extends InnerTypeTemplate
}
