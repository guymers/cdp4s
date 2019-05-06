import java.io.File

import io.circe.jawn.JawnParser
import protocol.chrome.ChromeProtocol
import protocol.template.EventsTemplate
import protocol.template.ModuleTemplate
import protocol.template.domain.DomainTemplate
import protocol.template.interpreter.WebSocketInterpreterTemplate
import protocol.template.types.ScalaChromeTypeContext
import protocol.template.types.TypesTemplate
import protocol.util.StringUtils

object ProtocolCodeGen {
  import StringUtils.escapeScalaVariable

  // could have experimental and non-experimental modules?

  private val parser = new JawnParser

  def generate(protocolJsonFile: File): Map[String, Seq[String]] = {

    val result = parser.decodeFile[ChromeProtocol](protocolJsonFile)
    val protocol = result.toTry.get

    Map(
      "cdp4s/domain/All.scala" -> ModuleTemplate.create(protocol.domains).toLines,
      "cdp4s/interpreter/WebSocketInterpreter.scala" -> WebSocketInterpreterTemplate.create(protocol.domains).toLines,
      "cdp4s/domain/event/events.scala" -> EventsTemplate.create(protocol.domains).toLines,
    ) ++
      protocol.domains.flatMap { domain =>
        implicit val ctx: ScalaChromeTypeContext = ScalaChromeTypeContext.defaultCtx(domain.domain)

        val filename = escapeScalaVariable(domain.domain)
        Map(
          s"cdp4s/domain/$filename.scala" -> DomainTemplate.create(domain).toLines,
          s"cdp4s/domain/model/$filename.scala" -> TypesTemplate.create(domain).toLines,
        )
      }
  }

}
