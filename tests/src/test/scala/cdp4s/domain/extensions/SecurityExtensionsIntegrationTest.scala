package cdp4s.domain.extensions

import cats.effect.IO
import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle
import cdp4s.domain.model.DOM
import cpd4s.test.InterpreterProvided
import org.scalatest.freespec.AsyncFreeSpec

import java.net.URI
import scala.annotation.tailrec
import scala.concurrent.duration.*

class SecurityExtensionsIntegrationTest extends AsyncFreeSpec with InterpreterProvided {
  import InterpreterProvided.*
  import cats.effect.unsafe.implicits.global

  "security" - {
    "does not ignore https errors" in {
      val uri = new URI("https://self-signed.badssl.com/")

      def program(implicit op: Operation[IO]) = for {
        _ <- PageHandle.navigate[IO](uri).timeout(10.seconds)
        document <- op.dom.getDocument(depth = Some(4))
      } yield {
        val head = findChildNode(document, List("HTML", "HEAD"))
        assert(head.flatMap(_.childNodeCount) == Some(0))
      }
      interpreter.run(program(_))
    }.unsafeToFuture()

    "ignore https errors if requested" in {
      val uri = new URI("https://self-signed.badssl.com/")

      def program(implicit op: Operation[IO]) = for {
        _ <- security.ignoreHTTPSErrors[IO]
        _ <- PageHandle.navigate[IO](uri).timeout(10.seconds)
        document <- op.dom.getDocument(depth = Some(4))
      } yield {
        val title = findChildNode(document, List("HTML", "HEAD", "TITLE"))
        val value = title.flatMap(_.children).flatMap(_.find(_.nodeName == "#text")).map(_.nodeValue)
        assert(value == Some("self-signed.badssl.com"))
      }
      interpreter.run(program(_))
    }.unsafeToFuture()
  }

  private def findChildNode(node: DOM.Node, path: List[String]) = {
    @tailrec
    def go(n: DOM.Node, p: List[String]): Option[DOM.Node] = p match {
      case Nil => Some(n)
      case head :: tail =>
        n.children.getOrElse(Vector.empty).find(_.nodeName == head) match {
          case None => None
          case Some(child) => go(child, tail)
        }
    }
    go(node, path)
  }
}
