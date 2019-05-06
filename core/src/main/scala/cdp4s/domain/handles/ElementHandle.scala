package cdp4s.domain.handles

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.domain.Operation
import cdp4s.domain.extensions
import cdp4s.domain.extensions.element
import cdp4s.domain.extensions.keys
import cdp4s.domain.extensions.mouse
import cdp4s.domain.model.Runtime

final case class ElementHandle(
  executionContextId: Runtime.ExecutionContextId,
  remoteObject: Runtime.RemoteObject,
) {

  def isVisible[F[_]: Monad](implicit op: Operation[F]): F[Boolean] = for {
    visible <- extensions.selector.isVisible(this)
  } yield visible

  def focus[F[_]: Monad](implicit op: Operation[F]): F[Unit] = element.focus(this)

  // https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/JSHandle.js#L237
  def hover[F[_]: Monad](implicit op: Operation[F]): F[Unit] = for {
    _ <- element.scrollIntoViewIfNeeded(this)
    (x, y) <- element.clickablePoint(this)
    _ <- mouse.move(x, y)
  } yield ()

  // https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/JSHandle.js#L246
  def click[F[_]: Monad](implicit op: Operation[F]): F[Unit] = for {
    _ <- element.scrollIntoViewIfNeeded(this)
    (x, y) <- element.clickablePoint(this)
    _ <- mouse.click(x, y)
  } yield ()

  def `type`[F[_]: Monad](text: String)(implicit op: Operation[F]): F[Unit] = for {
    _ <- focus
    _ <- keys.typeText(text)
  } yield ()

}
