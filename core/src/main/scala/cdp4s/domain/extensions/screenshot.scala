package cdp4s.domain.extensions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cdp4s.domain.Operation

object screenshot {

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def take[F[_]](
    blocker: Blocker,
    file: Path,
  )(implicit F: Sync[F], CS: ContextShift[F], op: Operation[F]): F[Path] = for {
    data <- op.page.captureScreenshot(format = cdp4s.domain.model.Page.params.Format.png.some)
    file <- blocker.delay {
      val f = if (file.toString.endsWith(".png")) file else file.resolveSibling(s"${file.getFileName.toString}.png")
      Files.write(f, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  } yield file
}
