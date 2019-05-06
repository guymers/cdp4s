package cdp4s.domain

import cdp4s.domain.event.Event

import scala.concurrent.duration.FiniteDuration

trait Events[F[_]] {

  // TODO return an identifier to allow cancellation
  def onEvent(f: PartialFunction[Event, F[Unit]]): F[Unit]

  def waitForEvent[E](f: PartialFunction[Event, E], timeout: FiniteDuration): F[Option[E]]
}
