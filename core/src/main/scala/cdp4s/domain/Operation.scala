package cdp4s.domain

trait Operation[F[_]] extends All[F] {
  val event: Events[F]
  val util: Util[F]
}
