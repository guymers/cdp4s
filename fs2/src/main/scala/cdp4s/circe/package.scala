package cdp4s

import java.nio.charset.MalformedInputException
import java.nio.charset.UnmappableCharacterException

import cats.syntax.either._
import cats.syntax.show._
import io.circe.Json
import io.circe.Printer
import io.circe.jawn.JawnParser
import scodec.Attempt
import scodec.Err
import scodec.bits.ByteVector

package object circe {

  private val parser = new JawnParser

  private val dropNullsPrinter = Printer.noSpaces.copy(dropNullValues = true)

  implicit val jsonCodec: scodec.Codec[Json] = scodec.codecs.bytes.exmap(
    bytes => {
      val r = parser.parseByteBuffer(bytes.toByteBuffer)
      Attempt.fromEither {
        r.leftMap(err => Err(show"Failed to parse body to json: $err"))
      }
    },
    json => {
      val r = ByteVector.encodeUtf8(json.pretty(dropNullsPrinter))
      Attempt.fromEither {
        r.leftMap {
          case _: MalformedInputException => "Json contains malformed input"
          case _: UnmappableCharacterException => "Json contains unmappable characters"
          case _ => "Json contains invalid characters"
        }.leftMap(Err.apply)
      }
    }
  )

}
