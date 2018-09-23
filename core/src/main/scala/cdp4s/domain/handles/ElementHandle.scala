package cdp4s.domain.handles

import cdp4s.domain.Operations
import cdp4s.domain.extensions.element
import cdp4s.domain.extensions.keys
import cdp4s.domain.extensions.mouse
import cdp4s.domain.model.Runtime
import freestyle.free._

/**
  * Based on https://github.com/GoogleChrome/puppeteer/blob/master/lib/ElementHandle.js
  */
final case class ElementHandle(
  executionContextId: Runtime.ExecutionContextId,
  remoteObject: Runtime.RemoteObject
) {

  def focus[F[_]](implicit O: Operations[F]): FreeS[F, Unit] = element.focus(this)

  def hover[F[_]](implicit O: Operations[F]): FreeS[F, Unit] = for {
    (x, y) <- element.visibleCenter(this)
    _ <- mouse.move(x, y)
  } yield ()

  def click[F[_]](implicit O: Operations[F]): FreeS[F, Unit] = for {
    (x, y) <- element.visibleCenter(this)
    _ <- mouse.click(x, y)
  } yield ()

  def `type`[F[_]](text: String)(implicit O: Operations[F]): FreeS[F, Unit] = for {
    _ <- focus
    _ <- keys.typeText(text)
  } yield ()

}
