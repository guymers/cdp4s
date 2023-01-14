package cdp4s.domain.extensions

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.NonEmptyParallel
import cats.effect.kernel.Resource
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import cats.syntax.parallel.*
import cdp4s.domain.Operation
import cdp4s.domain.model.Browser.BrowserContextID
import cdp4s.domain.model.Target.SessionID
import cdp4s.domain.model.Target.TargetID

object tab {

  /**
   * Initialize a tab
   *
   * @see
   *   [[https://github.com/puppeteer/puppeteer/blob/v5.3.1/src/common/FrameManager.ts#L121 FrameManager.ts]]
   */
  def initialize[F[_]](implicit F: Monad[F], P: NonEmptyParallel[F], op: Operation[F]): F[Unit] = for {
    _: Unit <- op.page.enable
    (_, _, _) <- (
      op.page.setLifecycleEventsEnabled(enabled = true),
      op.runtime.enable,
      op.network.enable(),
    ).parTupled
  } yield ()

  def createTab[F[_]](implicit F: Applicative[F], op: Operation[F]): Resource[F, SessionID] = for {
    browserContextId <- createBrowserContext
    targetId <- createTarget(browserContextId)
    sessionId <- createSession(targetId)
  } yield sessionId

  def createBrowserContext[F[_]](implicit F: Functor[F], op: Operation[F]): Resource[F, BrowserContextID] = {
    val acquire = {
      op.target.createBrowserContext()
    }
    def release(browserContextId: BrowserContextID): F[Unit] = {
      op.target.disposeBrowserContext(browserContextId)
    }
    Resource.make(acquire)(release)
  }

  def createTarget[F[_]](
    browserContextId: BrowserContextID,
  )(implicit F: Functor[F], op: Operation[F]): Resource[F, TargetID] = {
    val acquire = {
      op.target.createTarget("about:blank", browserContextId = browserContextId.some)
    }
    def release(targetId: TargetID): F[Unit] = {
      op.target.closeTarget(targetId).map(_ => ())
    }
    Resource.make(acquire)(release)
  }

  def createSession[F[_]](targetId: TargetID)(implicit F: Functor[F], op: Operation[F]): Resource[F, SessionID] = {
    val acquire = {
      op.target.attachToTarget(targetId, flatten = Some(true))
    }
    def release(sessionId: SessionID): F[Unit] = {
      op.target.detachFromTarget(targetId = targetId.some, sessionId = sessionId.some)
    }
    Resource.make(acquire)(release)
  }
}
