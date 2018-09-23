package protocol.template
package domain

import protocol.chrome.ChromeProtocolCommand
import protocol.template.types.ScalaChromeType

object DomainCommandReturnType {

  def create(command: ChromeProtocolCommand): ScalaChromeType = {
    val returns = command.returns.getOrElse(Seq.empty)
    returns.toList match {
      case Nil => ScalaChromeType.Fixed("Unit", Nil)
      case ret :: Nil => ScalaChromeType.chromeTypeToScala(ret.name, ret.`type`)
      case _ => ScalaChromeType.Obj(s"results.${command.name.capitalize}Result", Nil)
    }
  }
}
