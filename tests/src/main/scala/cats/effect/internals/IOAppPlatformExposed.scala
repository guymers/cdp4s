package cats.effect.internals

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer

object IOAppPlatformExposed {

  def defaultTimer: Timer[IO] = IOAppPlatform.defaultTimer
  def defaultContextShift: ContextShift[IO] = IOAppPlatform.defaultContextShift
}
