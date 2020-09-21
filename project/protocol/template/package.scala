package protocol

package object template {

  type Lines = Vector[String]

  implicit final class VectorExtension[T](val xs: Vector[T]) extends AnyVal {
    def zipWithNext: Vector[(T, Option[T])] = {
      val nextXs = xs.drop(1).map(Some.apply) :+ None
      xs.zip(nextXs)
    }
  }

  implicit final class LineExtension(val lines: Lines) extends AnyVal {
    def indent(level: Int): Lines = addIndentation(lines, level)
  }

  def addIndentation(lines: Lines, level: Int): Lines = {
    if (level <= 0) lines
    else {
      val indentation = Vector.fill(level)("  ").mkString
      lines.map { line =>
        if (line.nonEmpty) s"$indentation$line" else line
      }
    }
  }

  def descriptionToLines(description: Option[String], params: Vector[String]): Lines = {
    val descLines = description.map(_.split('\n').toVector).getOrElse(Vector.empty)
    Lines(
      Vector("/**"),
      (descLines ++ Vector("") ++ params).map(s => s"  * $s"),
      Vector("  */"),
    )
  }
}
