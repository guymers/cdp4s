package cdp4s.domain.extensions

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.domain.Operation
import cdp4s.domain.model.Input
import cdp4s.domain.model.Input.params.Type

object mouse {

  def move[F[_]: Monad](x: Double, y: Double)(implicit op: Operation[F]): F[Unit] = for {
    _ <- op.input.dispatchMouseEvent(Type.mouseMoved, x, y)
  } yield ()

  def click[F[_]: Monad](x: Double, y: Double)(implicit op: Operation[F]): F[Unit] = {
    val button = Input.params.Button.left

    for {
      _ <- move(x, y)
      _ <- op.input.dispatchMouseEvent(Type.mousePressed, x, y, button = Some(button), clickCount = Some(1))
      _ <- op.input.dispatchMouseEvent(Type.mouseReleased, x, y, button = Some(button), clickCount = Some(1))
    } yield ()
  }
}
