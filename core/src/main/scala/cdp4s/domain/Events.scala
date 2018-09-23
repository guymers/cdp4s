package cdp4s.domain

import cdp4s.domain.event.Event
import freestyle.free._

import scala.concurrent.duration.FiniteDuration

@free trait Events {

  def onEvent(f: PartialFunction[Event, FreeS[Operations.Op, Unit]]): FS[Unit]

  def waitForEvent[E](f: PartialFunction[Event, E], timeout: FiniteDuration): FS[Option[E]]
}
