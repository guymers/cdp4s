package cdp4s.domain

import scala.concurrent.duration.FiniteDuration

trait Util[F[_]] {
  def sleep(duration: FiniteDuration): F[Unit]

  def pure[T](v: T): F[T]
  def delay[T](v: Unit => T): F[T]

  def fail[T](t: Throwable): F[T]
  def handle[T](fs: F[T], f: PartialFunction[Throwable, T]): F[T]
  def handleWith[T](fs: F[T], f: PartialFunction[Throwable, F[T]]): F[T]
}
