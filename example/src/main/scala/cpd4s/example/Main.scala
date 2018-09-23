package cpd4s.example

import java.net.URI
import java.nio.channels.AsynchronousChannelGroup
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors

import scala.concurrent.duration._

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cdp4s.chrome.cli.ChromeLauncher
import cdp4s.chrome.http.ChromeHttpClient
import cdp4s.chrome.interpreter.runProgram
import cdp4s.domain.Extensions
import cdp4s.domain.Operations
import cdp4s.domain.event.PageEvent
import cdp4s.domain.event.RuntimeEvent
import cdp4s.domain.handles.PageHandle
import cdp4s.domain.model.Page
import cdp4s.domain.model.Runtime.ExecutionContextId
import freestyle.free.FreeS
import fs2.Stream

object Main extends IOApp {

  private val NumProcessors = Runtime.getRuntime.availableProcessors()

  private implicit val acg: AsynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool {
    Executors.newCachedThreadPool(Util.daemonThreadFactory("cpd4s-ACG"))
  }

  override def run(args: List[String]): IO[ExitCode] = {
    stream(args).to { s =>
      s.evalMap { result =>
        IO {
          println(s"${Thread.currentThread().getName} ${Instant.now()}: $result")
        }
      }
    }.compile.drain.map(_ => ExitCode.Success)
  }

  private def stream(args: List[String]) = {

    val chromePath = Paths.get(args.headOption.getOrElse("/usr/bin/chromium"))

    val chromeHttpClient = ChromeLauncher.launchHeadless[IO](chromePath)
      .flatMap(instance => Resource.liftF(ChromeHttpClient[IO](instance)))

    Stream.resource(chromeHttpClient)
      .flatMap { httpClient =>

        val programs = Stream.emits(List(
          //program[Operations.Op],
          //program[Operations.Op],
          //program[Operations.Op],
          program[Operations.Op]
        )).covary[IO]

        programs
          .map(p => runProgram(httpClient, p))
          .parJoin(NumProcessors)
          .collect {
            case Right(frameId) => frameId
          }
      }

  }

  private def program[F[_]](implicit ops: Operations[F]): FreeS[F, Page.FrameId] = {
    import cats.implicits._
    import freestyle.free._
    import freestyle.free.implicits._
    import ops.domain._

    val timeout = 10.seconds

    for {
      tempDir <- ops.util.delay { _ =>
        Files.createTempDirectory("cdp4s-example")
      }
      _ <- ops.util.delay { _ =>
        println("Using temp directory " + tempDir.toFile.getAbsolutePath)
      }
      _ <- (page.enable, runtime.enable).tupled.freeS
      _ <- Extensions.ignoreAllHTTPSErrors
      _ <- emulation.setDeviceMetricsOverride(1280, 1024, 0.0D, mobile = false)


      optPageHandle <- PageHandle.navigate(new URI("https://google.com"))(ops, timeout)
      pageHandle <- optPageHandle match {
        case None => ops.util.fail(new RuntimeException("Timeout navigating"))
        case Some(v) => ops.util.pure(v)
      }
      layoutMetrics <- page.getLayoutMetrics
      _ = println(layoutMetrics)
      image1Data <- page.captureScreenshot().map(Base64.getDecoder.decode)
      _ <- ops.util.delay { _ =>
        val file = tempDir.resolve("ss-01.png")
        Files.write(file, image1Data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      }


      searchTextElement <- pageHandle.find("#lst-ib")
      _ <- searchTextElement.get.`type`("test")
      image2Data <- page.captureScreenshot().map(Base64.getDecoder.decode)
      _ <- ops.util.delay { _ =>
        val file = tempDir.resolve("ss-02.png")
        Files.write(file, image2Data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      }


      searchButton <- pageHandle.find("input[type='submit'][name='btnK']")
      _ <- (
        searchButton.get.click,
        (
          ops.event.waitForEvent({ case e: RuntimeEvent.ExecutionContextCreated => e.context.id }, timeout),
          ops.event.waitForEvent({ case _: PageEvent.DomContentEventFired => () }, timeout)
        ).tupled.freeS
      ).tupled : FreeS[F, (Unit, (Option[ExecutionContextId], Option[Unit]))]
      image3Data <- page.captureScreenshot().map(Base64.getDecoder.decode)
      _ <- ops.util.delay { _ =>
        val file = tempDir.resolve("ss-03.png")
        Files.write(file, image3Data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      }
    } yield pageHandle.frameId
  }

}
