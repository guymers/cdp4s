package cdp4s.domain

import cdp4s.domain.event.Event

trait Events[F[_]] {

  /**
   * Run code when an event occurs.
   *
   * Returns an `F` that if run will remove the listener.
   */
  def onEvent(f: PartialFunction[Event, F[Unit]]): F[F[Unit]]

  /**
   * Register a listener that will be completed when the event occurs.
   */
  def waitForEvent[E](f: PartialFunction[Event, E]): F[F[E]]
}
