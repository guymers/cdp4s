package protocol.template

import cats.data.NonEmptyVector
import protocol.chrome.ChromeProtocolDomain
import protocol.chrome.ChromeProtocolTypeDefinition
import protocol.chrome.Deprecated
import protocol.chrome.Experimental
import protocol.template.types.ScalaChromeTypeContext
import protocol.util.StringUtils

object EventsTemplate {

  def create(domains: Vector[ChromeProtocolDomain]): EventsTemplate = {
    val templates = domains.flatMap(EventTemplate.create)

    EventsTemplate(templates)
  }

}

final case class EventsTemplate(
  templates: Vector[EventTemplate],
) {

  private val sortedTemplates = templates.sortBy(_.className)

  def toLines: Lines = {
    Lines(
      Line("/** Generated from Chromium /json/protocol */"),
      Line(""),
      Line("package cdp4s.domain.event"),
      Line(""),
      Line("sealed trait Event"),
      Line(""),
      Line("object Event {"),
      Line("  val decoders: Map[String, io.circe.Decoder[Event]] = {"),
      sortedTemplates.sortBy(_.className).zipWithNext.map { case (template, next) =>
        s"${template.className}.decoders.mapValues(_.map(e => e : Event))" + (if (next.isDefined) " ++" else "")
      }.indent(2),
      Line("  }.toMap"),
      Line(""),
      sortedTemplates.flatMap(_.toLines).indent(1),
      Line("}"),
    )
  }
}

object EventTemplate {

  def create(domain: ChromeProtocolDomain): Option[EventTemplate] = {
    domain.events.map { events =>
      val eventObjects = events.map { event =>
        val params = event.parameters.map(_.toVector).getOrElse(Vector.empty)
        EventObject(event.name, event.deprecated, event.experimental, params)
      }

      EventTemplate(domain.domain, eventObjects)
    }
  }

}

final case class EventTemplate(
  domain: String,
  eventObjects: NonEmptyVector[EventObject],
) {
  import StringUtils.escapeScalaVariable

  private implicit val eventCtx: ScalaChromeTypeContext = ScalaChromeTypeContext.eventCtx(domain)

  val className: String = escapeScalaVariable(domain)
  private val eventObjectTemplates = eventObjects.map { eventObject =>
    val objTemplate = ObjectTemplate.create(
      name = eventObject.name.capitalize,
      description = None,
      deprecated = eventObject.deprecated,
      experimental = eventObject.experimental,
      objExtends = Some(className),
      properties = eventObject.params,
    )
    (eventObject.name, objTemplate)
  }

  def toLines: Lines = {
    Lines(
      Line(s"sealed trait $className extends Event"),
      Line(""),
      Line(s"object $className {"),
      eventObjectTemplates.toVector.map(_._2).flatMap(_.toLines).indent(1),
      Lines(
        Line(""),
        Line(s"val decoders: Map[String, io.circe.Decoder[$className]] = Map("),
        eventObjectTemplates.toVector.map { case (eventName, eventObjectTemplate) =>
          val eventClassName = escapeScalaVariable(eventObjectTemplate.name)
          val className = if (eventObjectTemplate.parameterTemplates.isEmpty) {
            s"$eventClassName.type"
          } else eventClassName
          s""""$domain.$eventName" -> io.circe.Decoder[$className].map(e => e : $className),"""
        }.indent(1),
        Line(")"),
      ).indent(1),
      Line("}"),
      Line(""),
    )
  }
}

final case class EventObject(
  name: String,
  deprecated: Deprecated,
  experimental: Experimental,
  params: Vector[ChromeProtocolTypeDefinition],
)
