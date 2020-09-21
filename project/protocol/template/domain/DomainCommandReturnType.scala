package protocol.template
package domain

import cats.data.NonEmptyVector
import protocol.chrome.ChromeProtocolCommand
import protocol.template.types.ScalaChromeType

object DomainCommandReturnType {

  def create(command: ChromeProtocolCommand): ScalaChromeType = {
    command.returns match {
      case None => ScalaChromeType.Fixed("Unit", Nil)
      case Some(NonEmptyVector(ret, tail)) if tail.isEmpty => ScalaChromeType.chromeTypeToScala(ret.name, ret.`type`)
      case _ => ScalaChromeType.Obj(s"results.${command.name.capitalize}Result", Nil)
    }
  }
}
