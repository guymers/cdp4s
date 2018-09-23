
package protocol

package object template {

  implicit final class SeqExtension[T](val xs: Seq[T]) extends AnyVal {
    def zipWithNext: Seq[(T, Option[T])] = {
      val nextXs = xs.drop(1).map(Some.apply) :+ None
      xs.zip(nextXs)
    }
  }

  implicit final class LineExtension(val lines: Seq[String]) extends AnyVal {
    def indent(level: Int): Seq[String] = addIndentation(lines, level)
  }

  def addIndentation(lines: Seq[String], level: Int): Seq[String] = {
    if (level <= 0) lines
    else {
      val indentation = List.fill(level)("  ").mkString
      lines.map { line =>
        if (line.nonEmpty) s"$indentation$line" else line
      }
    }
  }
}
