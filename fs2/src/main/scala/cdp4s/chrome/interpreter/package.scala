package cdp4s.chrome

import cdp4s.domain.event.Event

package object interpreter {

  type EventListener[F[_]] = PartialFunction[Event, F[Unit]]
}
