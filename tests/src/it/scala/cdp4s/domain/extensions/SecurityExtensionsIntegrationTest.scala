package cdp4s.domain.extensions

import cdp4s.domain.Operation
import cdp4s.domain.handles.PageHandle
import cdp4s.domain.model.DOM
import cpd4s.test.InterpreterProvidedIntegrationTest
import zio.Task
import zio.durationInt
import zio.interop.catz.*
import zio.test.*

import java.net.URI
import scala.annotation.tailrec

object SecurityExtensionsIntegrationTest extends InterpreterProvidedIntegrationTest {

  override val spec = suite("SecurityExtensions")(
    suite("security")(
      test("does not ignore https errors") {
        val uri = new URI("https://self-signed.badssl.com/")

        def program(implicit op: Operation[Task]) = for {
          _ <- PageHandle.navigate[Task](uri).timeout(10.seconds)
          document <- op.dom.getDocument(depth = Some(4))
        } yield {
          val head = findChildNode(document, List("HTML", "HEAD"))
          assertTrue(head.flatMap(_.childNodeCount) == Some(0))
        }

        interpreter.flatMap(_.run(program(_)))
      },
      test("ignore https errors if requested") {
        val uri = new URI("https://self-signed.badssl.com/")

        def program(implicit op: Operation[Task]) = for {
          _ <- security.ignoreHTTPSErrors[Task]
          _ <- PageHandle.navigate[Task](uri).timeout(10.seconds)
          document <- op.dom.getDocument(depth = Some(4))
        } yield {
          val title = findChildNode(document, List("HTML", "HEAD", "TITLE"))
          val value = title.flatMap(_.children).flatMap(_.find(_.nodeName == "#text")).map(_.nodeValue)
          assertTrue(value == Some("self-signed.badssl.com"))
        }

        interpreter.flatMap(_.run(program(_)))
      },
    ),
  )

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
