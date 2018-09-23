package cdp4s.domain.extensions

import cats.instances.list._
import cats.syntax.traverse._
import cdp4s.domain.Operations
import cdp4s.domain.model.Input.params.Type
import freestyle.free._

object keys {

  def `type`[F[_]](char: Char)(implicit O: Operations[F]): FreeS[F, Unit] = {
    import O.domain._

    val text = char.toString

    for {
      _ <- input.dispatchKeyEvent(Type.keyDown, text = Some(text), unmodifiedText = Some(text))
      _ <- input.dispatchKeyEvent(Type.keyUp)
    } yield ()
  }

  def typeText[F[_]](text: String)(implicit O: Operations[F]): FreeS[F, Unit] = for {
    _ <- text.toList.traverse(c => `type`(c))
  } yield ()
}
