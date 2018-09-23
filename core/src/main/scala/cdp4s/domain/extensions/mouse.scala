package cdp4s.domain.extensions

import cdp4s.domain.Operations
import cdp4s.domain.model.Input
import cdp4s.domain.model.Input.params.Type
import freestyle.free._

object mouse {

  def move[F[_]](x: Double, y: Double)(implicit O: Operations[F]): FreeS[F, Unit] = {
    import O.domain._

    for {
      _ <- input.dispatchMouseEvent(Type.mouseMoved, x, y)
    } yield ()
  }

  def click[F[_]](x: Double, y: Double)(implicit O: Operations[F]): FreeS[F, Unit] = {
    import O.domain._

    val button = Input.params.Button.left

    for {
      _ <- move(x, y)
      _ <- input.dispatchMouseEvent(Type.mousePressed, x, y, button = Some(button), clickCount = Some(1))
      _ <- input.dispatchMouseEvent(Type.mouseReleased, x, y, button = Some(button), clickCount = Some(1))
    } yield ()
  }
}
