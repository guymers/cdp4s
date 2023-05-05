package protocol.template
package domain

import cats.data.NonEmptyVector
import protocol.chrome.ChromeProtocolDomain
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object DomainTemplate {

  def create(domain: ChromeProtocolDomain): DomainTemplate = {
    val commandTemplates = domain.commands.map(DomainCommandTemplate.create)

    DomainTemplate(domain.domain, commandTemplates)
  }
}

final case class DomainTemplate(
  name: String,
  commandTemplates: NonEmptyVector[DomainCommandTemplate],
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Lines = {
    val safeName = escapeScalaVariable(name)

    Lines(
      Line("/** Generated from Chromium /json/protocol */"),
      Line(""),
      Line("package cdp4s.domain"),
      Line(""),
      Line("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
      Line(s"trait $safeName[F[_]] {"),
      commandTemplates.toVector.flatMap(_.toLines).indent(1),
      Line("}"),
    )
  }
}
