package protocol.template
package types

import protocol.chrome.ChromeProtocolType
import protocol.util.StringUtils

import scala.annotation.tailrec

sealed trait ScalaChromeType {
  def wrappers: List[ScalaChromeTypeWrapper]
}

object ScalaChromeType {
  import StringUtils.escapeScalaVariable

  final case class Fixed(`type`: String, wrappers: List[ScalaChromeTypeWrapper]) extends ScalaChromeType
  final case class Obj(name: String, wrappers: List[ScalaChromeTypeWrapper]) extends ScalaChromeType
  final case class Enum(name: String, wrappers: List[ScalaChromeTypeWrapper]) extends ScalaChromeType
  final case class Reference(domain: Option[String], ref: String, wrappers: List[ScalaChromeTypeWrapper]) extends ScalaChromeType

  def chromeTypeToScala(name: String, `type`: ChromeProtocolType): ScalaChromeType = {
    import ChromeProtocolType._

    @tailrec
    def go(tpe: ChromeProtocolType, wrappers: List[ScalaChromeTypeWrapper]): ScalaChromeType = {

      def optWrappers(w: List[ScalaChromeTypeWrapper]) = if (tpe.optional) ScalaChromeTypeWrapper.Optional :: w else w

      tpe match {
        case any(_) => Fixed("_root_.io.circe.Json", optWrappers(wrappers))
        case binary(_) => Fixed("scala.Array[Byte]", optWrappers(wrappers))
        case string(_) => Fixed("scala.Predef.String", optWrappers(wrappers))
        case integer(_) => Fixed("scala.Int", optWrappers(wrappers))
        case number(_) => Fixed("scala.Double", optWrappers(wrappers))
        case boolean(_) => Fixed("scala.Boolean", optWrappers(wrappers))
        case array(items, _) => go(items, optWrappers(ScalaChromeTypeWrapper.Array :: wrappers))
        case obj(properties, _) => if (properties.nonEmpty) {
          Obj(name, optWrappers(wrappers))
        } else {
          Fixed("_root_.io.circe.JsonObject", optWrappers(wrappers))
        }
        case enum(_, _) => Enum(name, optWrappers(wrappers))
        case reference(refDomain, ref, _) => Reference(refDomain, ref, optWrappers(wrappers))
      }
    }
    go(`type`, Nil)
  }

  def toTypeString(tpe: ScalaChromeType)(implicit ctx: ScalaChromeTypeContext): String = {
    val toTypeStr = ScalaChromeTypeWrapper.toTypeString(tpe.wrappers)
    tpe match {
      case Fixed(t, _) => toTypeStr(t)
      case Obj(name, _) => toTypeStr(s"${ctx.objPackage}.$name")
      case Enum(name, _) => toTypeStr(s"${ctx.enumPackage}.$name")
      case Reference(domain, ref, _) => toTypeStr(s"${ctx.referencePackage}.${escapeScalaVariable(domain.getOrElse(ctx.domain))}.$ref")
    }
  }
}

sealed trait ScalaChromeTypeWrapper
object ScalaChromeTypeWrapper {
  case object Optional extends ScalaChromeTypeWrapper
  case object Array extends ScalaChromeTypeWrapper

  def toTypeString(wrappers: Seq[ScalaChromeTypeWrapper]): String => String = str => {
    wrappers.foldRight(str) {
      case (Optional, t) => s"scala.Option[$t]"
      case (Array, t) => s"scala.collection.immutable.Vector[$t]"
    }
  }
}
