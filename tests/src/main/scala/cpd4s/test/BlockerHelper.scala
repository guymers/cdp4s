package cpd4s.test

import java.util.concurrent.Executors

import cats.effect.Blocker
import fs2.internal.ThreadFactoriesExposed

object BlockerHelper {

  val blocker: Blocker = Blocker.liftExecutorService {
    val threadFactory = ThreadFactoriesExposed.named("blocking", daemon = true)
    Executors.newCachedThreadPool(threadFactory)
  }

}
