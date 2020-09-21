package protocol.template

import protocol.chrome.ChromeProtocolDomain
import protocol.util.StringUtils

object ModuleTemplate {

  def create(domains: Vector[ChromeProtocolDomain]): ModuleTemplate = {
    ModuleTemplate(domains.map(_.domain))
  }
}

final case class ModuleTemplate(
  domains: Vector[String],
) {

  def toLines: Lines = {
    Lines(
      Line("/** Generated from Chromium /json/protocol */"),
      Line(""),
      Line("package cdp4s.domain"),
      Line(""),
      Line("trait All[F[_]] {"),
      domains.sorted.map(domain => s"val ${StringUtils.unCamelCase(domain)}: $domain[F]").indent(1),
      Line("}"),
    )
  }
}
