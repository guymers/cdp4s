package cdp4s

import java.util.concurrent.CompletableFuture

trait Runtime[F[_]] {
  def unsafeRun[A](fa: F[A]): CompletableFuture[A]
  def unsafeRunSync[A](fa: F[A]): A
}

object Runtime {

  // https://blog.7mind.io/no-more-orphans.html

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  implicit def catsEffectIORuntime(implicit runtime: cats.effect.unsafe.IORuntime): Runtime[cats.effect.IO] = {
    new Runtime[cats.effect.IO] {
      override def unsafeRun[A](fa: cats.effect.IO[A]) = {
        fa.unsafeToCompletableFuture()
      }
      override def unsafeRunSync[A](fa: cats.effect.IO[A]) = {
        fa.unsafeRunSync()
      }
    }
  }
}
