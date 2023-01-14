package protocol.chrome

import cats.syntax.either.*
import io.circe.Decoder
import io.circe.DecodingFailure

sealed trait ChromeProtocolType {
  def optional: Boolean
}

object ChromeProtocolType {

  final case class any(optional: Boolean) extends ChromeProtocolType
  final case class binary(optional: Boolean) extends ChromeProtocolType
  final case class string(optional: Boolean) extends ChromeProtocolType
  final case class integer(optional: Boolean) extends ChromeProtocolType
  final case class number(optional: Boolean) extends ChromeProtocolType
  final case class boolean(optional: Boolean) extends ChromeProtocolType
  final case class array(items: ChromeProtocolType, optional: Boolean) extends ChromeProtocolType
  final case class obj(properties: Vector[ChromeProtocolTypeDefinition], optional: Boolean) extends ChromeProtocolType
  final case class enum(values: Set[String], optional: Boolean) extends ChromeProtocolType
  final case class reference(domain: Option[String], ref: String, optional: Boolean) extends ChromeProtocolType

  // it seems optional is sometimes a string
  private val booleanDecoder: Decoder[Boolean] = Decoder.decodeBoolean or Decoder.decodeString.emap {
    case "true" => Right(true)
    case "false" => Right(false)
    case _ => Left("Expected either true or false")
  }

  implicit val decoder: Decoder[ChromeProtocolType] = Decoder.instance { c =>
    for {
      tpe <- c.downField("type").as[Option[String]]
      ref <- c.downField("$ref").as[Option[String]]
      isOptional <- c.downField("optional").as[Option[Boolean]](Decoder.decodeOption(booleanDecoder))
      optional = isOptional.getOrElse(false)
      result <- {
        val decodedType = tpe.map {
          case "any" => any(optional).asRight
          case "binary" => binary(optional).asRight
          case "string" =>
            c.downField("enum").as[Option[Vector[String]]].map {
              case None => string(optional)
              case Some(enumValues) => enum(enumValues.toSet, optional)
            }
          case "integer" => integer(optional).asRight
          case "number" => number(optional).asRight
          case "boolean" => boolean(optional).asRight
          case "array" =>
            c.downField("items").as[ChromeProtocolType].map { items =>
              array(items, optional)
            }
          case "object" =>
            c.downField("properties").as[Option[Vector[ChromeProtocolTypeDefinition]]].map { properties =>
              obj(properties.getOrElse(Vector.empty), optional)
            }
          case _ => DecodingFailure(s"'$tpe' is not a valid type", c.history).asLeft
        }
        decodedType
          .orElse {
            ref.map { r =>
              val parts = r.split('.')
              val (domain, name) = {
                if (parts.length > 1) (parts.headOption, parts.drop(1).mkString("."))
                else (None, parts.mkString("."))
              }
              reference(domain, name, optional).asRight
            }
          }
          .getOrElse {
            DecodingFailure("Missing 'type' and '$ref' fields", c.history).asLeft
          }
      }
    } yield result
  }
}
