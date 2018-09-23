package protocol.template

import protocol.chrome.ChromeProtocolDomain
import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object EventsTemplate {

  def create(domains: Seq[ChromeProtocolDomain]): EventsTemplate = {
    val templates = domains.map(EventTemplate.create)

    EventsTemplate(templates)
  }

}

final case class EventsTemplate(
  templates: Seq[EventTemplate],
) {

  private val sortedTemplates = templates.sortBy(_.className)

  def toLines: Seq[String] = {
    Seq(
      Seq("/** Generated from Chromium /json/protocol */"),
      Seq(""),
      Seq("package cdp4s.domain.event"),
      Seq(""),
      Seq("sealed trait Event"),
      Seq(""),
      Seq("object Event {"),
      Seq("  val decoders: Map[String, io.circe.Decoder[Event]] = {"),
      sortedTemplates.sortBy(_.className).zipWithNext.map { case (template, next) =>
        s"${template.className}.decoders.mapValues(_.map(e => e : Event))" + (if (next.isDefined) " ++" else "")
      }.indent(2),
      Seq("  }"),
      Seq("}"),
      Seq(""),
      sortedTemplates.flatMap(_.toLines)
    ).flatten
  }
}

object EventTemplate {

  def create(domain: ChromeProtocolDomain): EventTemplate = {
    val events = domain.events.getOrElse(Seq.empty)
    val eventObjects = events.map { event =>
      val params = event.parameters.getOrElse(Seq.empty)
      EventObject(event.name, params)
    }

    EventTemplate(domain.domain, eventObjects)
  }

}

final case class EventTemplate(
  domain: String,
  eventObjects: Seq[EventObject],
) {
  import StringUtils.escapeScalaVariable

  private implicit val eventCtx: ScalaChromeTypeContext = ScalaChromeTypeContext.eventCtx(domain)

  val className: String = s"${escapeScalaVariable(domain)}Event"
  private val eventObjectTemplates = eventObjects.map { eventObject =>
    (eventObject.name, ObjectTemplate.create(eventObject.name.capitalize, Some(className), eventObject.params))
  }

  def toLines: Seq[String] = {
    Seq(
      Seq(s"sealed trait $className extends Event"),
      Seq(""),
      Seq(s"object $className {"),
      if (eventObjectTemplates.nonEmpty) {
        eventObjectTemplates.map(_._2).flatMap(_.toLines).indent(1),
      } else Seq.empty,
      Seq(
        Seq(""),
        Seq(s"val decoders: Map[String, io.circe.Decoder[$className]] = Map("),
        eventObjectTemplates.zipWithNext.map { case ((eventName, eventObjectTemplate), next) =>
          val eventClassName = escapeScalaVariable(eventObjectTemplate.name)
          val line = s"""  "$domain.$eventName" -> io.circe.Decoder[$eventClassName].map(e => e : $className)"""
          line ++ (if (next.isDefined) "," else "")
        },
        Seq(")"),
      ).flatten.indent(1),
      Seq("}"),
      Seq(""),
    ).flatten
  }
}

final case class EventObject(
  name: String,
  params: Seq[ChromeProtocolTypeDefinition],
)
