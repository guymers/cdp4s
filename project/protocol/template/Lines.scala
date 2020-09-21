package protocol.template

object Line {

  def apply(line: String): Lines = Vector(line)
}

object Lines {

  def apply(line: Vector[String], lines: Vector[String]*): Lines = {
    lines.foldLeft(line)(_ ++ _)
  }
}
