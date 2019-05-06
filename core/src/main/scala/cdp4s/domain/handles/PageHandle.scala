package cdp4s.domain.handles

import java.net.URI

import scala.concurrent.duration._

import cats.Monad
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.syntax.concurrent._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.domain.Operation
import cdp4s.domain.event.PageEvent
import cdp4s.domain.event.RuntimeEvent
import cdp4s.domain.model.Page
import cdp4s.domain.model.Runtime

object PageHandle {

  /**
    * Navigate to a url.
    *
    * Ensure that page and runtime events are enabled before calling this method.
    */
  def navigate[F[_]](url: URI, timeout: FiniteDuration)(implicit F: Concurrent[F], op: Operation[F]): F[Option[PageHandle]] = {
    navigating(op.page.navigate(url.toString), timeout).map {
      _.map { case (navigateResult, execCtxId) => PageHandle(navigateResult.frameId, execCtxId) }
    }
  }

  /**
    * Perform an action that is expected to navigate to another page.
    *
    * Ensure that page and runtime events are enabled before calling this method.
    */
  def navigating[F[_], T](
    action: F[T],
    timeout: FiniteDuration
  )(implicit F: Concurrent[F], op: Operation[F]): F[Option[(T, Runtime.ExecutionContextId)]] = {
    val waitForEvents = for {
      execCtxIdFiber <- Resource.make(
        op.event.waitForEvent({ case e: RuntimeEvent.ExecutionContextCreated => e.context.id }, timeout).start
      )(_.cancel)
      // puppeteer allows waiting on one of 4 events
      contentFiber <- Resource.make(
        op.event.waitForEvent({ case _: PageEvent.DomContentEventFired => () }, timeout).start
      )(_.cancel)
    } yield (execCtxIdFiber, contentFiber)

    waitForEvents.use { case (execCtxIdFiber, contentFiber) =>
      for {
        result <- action
        execCtxId <- execCtxIdFiber.join
        content <- contentFiber.join
      } yield {
        for {
          execCtxId <- execCtxId
          _ <- content
        } yield {
          (result, execCtxId)
        }
      }
    }
  }
}

final case class PageHandle(
  frameId: Page.FrameId,
  executionContextId: Runtime.ExecutionContextId
) {
  import cdp4s.domain.extensions

  def find[F[_]: Monad](selector: String)(implicit op: Operation[F]): F[Option[ElementHandle]] = for {
    element <- extensions.selector.find(executionContextId, selector)
  } yield element

}
