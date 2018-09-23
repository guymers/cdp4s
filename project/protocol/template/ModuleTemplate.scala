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
      Seq("import freestyle.free.module"),
      Seq(""),
      Seq(s"@module trait Domains {"),
      domains.sorted.map(domain => s"val ${StringUtils.unCamelCase(domain)}: $domain").indent(1),
      Seq("}"),
    ).flatten
  }
}
