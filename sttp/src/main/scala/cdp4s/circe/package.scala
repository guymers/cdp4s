package cdp4s

import io.circe.Encoder
import io.circe.Printer
import io.circe.jawn.JawnParser
import io.circe.syntax._

package object circe {

  val parser: JawnParser = new JawnParser

  private val dropNullsPrinter = Printer.noSpaces.copy(dropNullValues = true)

  def print[V: Encoder](v: V): String = v.asJson.printWith(dropNullsPrinter)
}
