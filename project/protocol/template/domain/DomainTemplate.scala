package protocol.template
package domain

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
  commandTemplates: Seq[DomainCommandTemplate],
) {
  import StringUtils.escapeScalaVariable

  def toLines(implicit ctx: ScalaChromeTypeContext): Seq[String] = {
    val safeName = escapeScalaVariable(name)

    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.domain"),
      Seq(""),
      Seq("""@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))"""),
      Seq(s"trait $safeName[F[_]] {"),
      commandTemplates.flatMap(_.toLines).indent(1),
      Seq("}"),
    ).flatten
  }
}
