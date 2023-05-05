package cdp4s.domain.handles

import java.net.URI

import cats.Monad
import cats.MonadError
import cats.NonEmptyParallel
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.parallel.*
import cdp4s.domain.Operation
import cdp4s.domain.event.Event
import cdp4s.domain.model.Page
import cdp4s.domain.model.Runtime

object PageHandle {

  /**
    * Navigate to a url.
    *
    * Ensure that page and runtime events are enabled before calling this method.
    */
  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def navigate[F[_]](
    url: URI,
  )(implicit F: Monad[F], P: NonEmptyParallel[F], op: Operation[F]): F[PageHandle] = for {
    (navigateResult, execCtxId) <- navigating(op.page.navigate(url.toString))
  } yield PageHandle(navigateResult.frameId, execCtxId)

  /**
    * Perform an action that is expected to navigate to another page.
    *
    * Ensure that page and runtime events are enabled before calling this method.
    */
  def navigating[F[_], T](
    action: F[T],
  )(implicit F: Monad[F], P: NonEmptyParallel[F], op: Operation[F]): F[(T, Runtime.ExecutionContextId)] = for {
    (executionContextCreatedF, domContentEventFiredF) <- (
      op.event.waitForEvent({ case e: Event.Runtime.ExecutionContextCreated => e.context.id }),
      op.event.waitForEvent({ case _: Event.Page.DomContentEventFired => () }),
    ).parTupled
    result <- action
    (execCtxId, _) <- (
      executionContextCreatedF,
      domContentEventFiredF,
    ).parTupled
  } yield (result, execCtxId)
}

final case class PageHandle(
  frameId: Page.FrameId,
  executionContextId: Runtime.ExecutionContextId,
) {
  import cdp4s.domain.extensions

  def find[F[_]](
    selector: String
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Option[ElementHandle]] = for {
    element <- extensions.selector.find(executionContextId, selector)
  } yield element

}
