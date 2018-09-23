package cdp4s.domain

import freestyle.free._

import scala.concurrent.duration.FiniteDuration

@free trait Util {
  def sleep(duration: FiniteDuration): FS[Unit]
  def pure[T](v: T): FS[T]
  def delay[T](v: Unit => T): FS[T]
  def fail[T](t: Throwable): FS[T]
}
