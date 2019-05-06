package protocol.template

import protocol.chrome.ChromeProtocolDomain
import protocol.util.StringUtils

object ModuleTemplate {

  def create(domains: Seq[ChromeProtocolDomain]): ModuleTemplate = {
    ModuleTemplate(domains.map(_.domain))
  }
}

final case class ModuleTemplate(
  domains: Seq[String]
) {

  def toLines: Seq[String] = {
    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.domain"),
      Seq(""),
      Seq("trait All[F[_]] {"),
      domains.sorted.map(domain => s"val ${StringUtils.unCamelCase(domain)}: $domain[F]").indent(1),
      Seq("}"),
    ).flatten
  }
}
