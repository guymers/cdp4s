package protocol.util

object StringUtils {

  private val reservedNames = Set(
    "catch",
    "implicit",
    "new",
    "null",
    "object",
    "override",
    "return",
    "this",
    "type",
    "with",
  )

  def escapeScalaVariable(name: String): String = {
    val escapedName =
      if (name.startsWith("-")) s"Negative${name.substring(1)}"
      else if (reservedNames.contains(name)) s"`$name`"
      else name
    escapedName.replaceAll("-", "_")
  }

  /**
    * ApplicationCache -> applicationCache
    * CSS -> css
    * DOMDebugger -> domDebugger
    *
    * // FIXME def indexeddb: IndexedDB[F]
    */
  def unCamelCase(str: String): String = {
    val strLower = str.headOption.map(c => c.toLower +: str.drop(1)).getOrElse("")
    val strNext = str.drop(1).map(Some.apply) :+ None
    strLower.zip(strNext).map {
      case (char, Some(nextChar)) => if (nextChar.isUpper) char.toLower else char
      case (char, None) => char.toLower
    }.mkString
  }
}
