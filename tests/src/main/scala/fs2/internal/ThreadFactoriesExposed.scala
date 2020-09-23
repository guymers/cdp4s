package fs2.internal

import java.util.concurrent.ThreadFactory

object ThreadFactoriesExposed {

  def named(
    threadPrefix: String,
    daemon: Boolean,
  ): ThreadFactory = ThreadFactories.named(threadPrefix, daemon)
}
