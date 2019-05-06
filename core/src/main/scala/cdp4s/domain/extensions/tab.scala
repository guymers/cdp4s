package cdp4s.domain.extensions

import cats.Applicative
import cats.Functor
import cats.effect.Resource
import cats.syntax.functor._
import cats.syntax.option._
import cdp4s.domain.Operation
import cdp4s.domain.model.Target.BrowserContextID
import cdp4s.domain.model.Target.SessionID
import cdp4s.domain.model.Target.TargetID

object tab {

  def createTab[F[_]: Applicative](implicit op: Operation[F]): Resource[F, SessionID] = for {
    browserContextId <- createBrowserContext
    targetId <- createTarget(browserContextId)
    sessionId <- createSession(targetId)
  } yield sessionId

  def createBrowserContext[F[_] : Functor](implicit op: Operation[F]): Resource[F, BrowserContextID] = {
    val acquire = {
      op.target.createBrowserContext
    }
    def release(browserContextId: BrowserContextID): F[Unit] = {
      op.target.disposeBrowserContext(browserContextId)
    }
    Resource.make(acquire)(release)
  }

  def createSession[F[_] : Functor](targetId: TargetID)(implicit op: Operation[F]): Resource[F, SessionID] = {
    val acquire = {
      op.target.attachToTarget(targetId, flatten = Some(true))
    }
    def release(sessionId: SessionID): F[Unit] = {
      op.target.detachFromTarget(targetId = targetId.some, sessionId = sessionId.some)
    }
    Resource.make(acquire)(release)
  }

  def createTarget[F[_] : Functor](browserContextId: BrowserContextID)(implicit op: Operation[F]): Resource[F, TargetID] = {
    val acquire = {
      op.target.createTarget("about:blank", browserContextId = browserContextId.some)
    }
    def release(targetId: TargetID): F[Unit] = {
      op.target.closeTarget(targetId).map(_ => ())
    }
    Resource.make(acquire)(release)
  }
}
