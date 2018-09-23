package cpd4s.example

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object Util {

  //  private def daemonThreadFactory(name: String) = fs2.internal.ThreadFactories.named(name, daemon = true)
  def daemonThreadFactory(name: String): ThreadFactory = new ThreadFactory {
    val defaultThreadFactory = Executors.defaultThreadFactory()
    val idx = new AtomicInteger(0)
    def newThread(r: Runnable): Thread = {
      val t = defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(s"$name-${idx.incrementAndGet()}")
      t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
        def uncaughtException(t: Thread, e: Throwable): Unit = {
          ExecutionContext.defaultReporter(e)
          e match {
            case NonFatal(_) => ()
            case _       => System.exit(-1)
          }
        }
      })
      t
    }
  }
}
