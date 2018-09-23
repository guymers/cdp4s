import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import scala.collection.JavaConverters._

object FileUtils {

  def writeLinesToFile(file: Path, lines: Seq[String]): Path = {
    Files.write(file, lines.asJava, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }
}
