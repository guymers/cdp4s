package cdp4s.domain.extensions

import cats.effect.kernel.Sync
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import cdp4s.domain.Operation

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object screenshot {

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def take[F[_]](
    file: Path,
  )(implicit F: Sync[F], op: Operation[F]): F[Path] = for {
    data <- op.page.captureScreenshot(format = cdp4s.domain.model.Page.params.Format.png.some)
    file <- F.blocking {
      val f = if (file.toString.endsWith(".png")) file else file.resolveSibling(s"${file.getFileName.toString}.png")
      Files.write(f, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  } yield file
}
