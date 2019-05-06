package cpd4s

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.arrow.FunctionK
import cats.effect.Concurrent
import cats.effect.Fiber
import cats.effect.Resource
import cats.effect.syntax.concurrent._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import cats.~>

package object example {

  // https://olegpy.com/fiber-safety/
  def parallelFromConcurrent[F[_]](implicit F: Concurrent[F]): Parallel[F, F] = {
    def startR[A](f: F[A]): Resource[F, Fiber[F, A]] = Resource.make(f.start)(_.cancel)

    val parallelApplicative = new Applicative[F] {
      override def pure[A](x: A): F[A] = F.pure(x)
      override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = {
        product(fa, ff).map { case (a, f) => f(a) }
      }
      override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
        (startR(fa), startR(fb)).tupled.use { case (fa, fb) =>
          F.racePair(fa.join.attempt, fb.join.attempt).flatMap {
            case Left((Left(ex), _))    => F.raiseError(ex)
            case Right((_, Left(ex)))   => F.raiseError(ex)
            case Left((Right(a), fb2))  => (pure(a), fb2.join.rethrow).tupled
            case Right((fa2, Right(b))) => (fa2.join.rethrow, pure(b)).tupled
          }
        }
      }
    }
    new Parallel[F, F] { // [M, F]
      override def applicative: Applicative[F] = parallelApplicative
      override def monad: Monad[F] /*Monad[M]*/ = F
      override def sequential: F ~> F /*F ~> M*/ = FunctionK.id
      override def parallel: F ~> F /*M ~> F*/ = FunctionK.id
    }
  }
}
