package cdp4s.domain.extensions

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cdp4s.domain.Operation
import cdp4s.domain.model.Input.params.Type

object keys {

  def `type`[F[_]: Monad](char: Char)(implicit op: Operation[F]): F[Unit] = {
    val text = char.toString

    for {
      _ <- op.input.dispatchKeyEvent(Type.keyDown, text = Some(text), unmodifiedText = Some(text))
      _ <- op.input.dispatchKeyEvent(Type.keyUp)
    } yield ()
  }

  def typeText[F[_]: Monad](text: String)(implicit op: Operation[F]): F[Unit] = {
    text.toList.traverse(c => `type`[F](c)).map(_ => ())
  }
}
