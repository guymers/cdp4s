package cdp4s.domain.handles

import java.net.URI

import scala.concurrent.duration._

import cdp4s.domain.Operations
import cdp4s.domain.event.PageEvent
import cdp4s.domain.event.RuntimeEvent
import cdp4s.domain.model.Page
import cdp4s.domain.model.Page.results.NavigateResult
import cdp4s.domain.model.Runtime
import cdp4s.domain.model.Runtime.ExecutionContextId
import freestyle.free._

// FIXME cleanup
import cats.implicits._
import freestyle.free.implicits._

object PageHandle {

  def navigate[F[_]](url: URI)(implicit O: Operations[F], timeout: FiniteDuration): FreeS[F, Option[PageHandle]] = {
    import O.domain._

    for {
      (navigateResult, execId, content) <- (
        page.navigate(url.toString),
        O.event.waitForEvent({ case e: RuntimeEvent.ExecutionContextCreated => e.context.id }, timeout),
        O.event.waitForEvent({ case _: PageEvent.DomContentEventFired => () }, timeout)
      ).tupled.freeS : FreeS[F, (NavigateResult, Option[ExecutionContextId], Option[Unit])]
    } yield {
      for {
        execId <- execId
        _ <- content
      } yield {
        PageHandle(navigateResult.frameId, execId)
      }
    }
  }
}

final case class PageHandle(
  frameId: Page.FrameId,
  executionContextId: Runtime.ExecutionContextId
) {

  def find[F[_]](selector: String)(implicit O: Operations[F]): FreeS[F, Option[ElementHandle]] = {
    import cdp4s.domain.extensions

    for {
      element <- extensions.selector.find(executionContextId, selector)
    } yield element
  }
}
