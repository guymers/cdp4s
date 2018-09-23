package cdp4s.domain.extensions

import cdp4s.domain.Operations
import cdp4s.domain.handles.ElementHandle
import cdp4s.domain.model.Runtime
import freestyle.free._
import io.circe.syntax._

object element {

  def focus[F[_]](element: ElementHandle)(implicit O: Operations[F]): FreeS[F, Unit] = for {
    _ <- execute.callFunction(
      element.executionContextId,
      "element => element.focus()",
      Seq(remoteObjectToCallArgument(element.remoteObject))
    )
  } yield ()

  def scrollIntoViewIfNeeded[F[_]](element: ElementHandle)(implicit O: Operations[F]): FreeS[F, Unit] = {

    val functionDec =
      s"""element => {
         |  if (!element.ownerDocument.contains(element)) {
         |    return 'Node is detached from document';
         |  }
         |  if (element.nodeType !== Node.ELEMENT_NODE) {
         |    return 'Node is not of type HTMLElement';
         |  }
         |  element.scrollIntoViewIfNeeded();
         |  return '';
         |}
       """.stripMargin

    for {
      result <- execute.callFunction(
        element.executionContextId,
        functionDec,
        Seq(remoteObjectToCallArgument(element.remoteObject))
      )

//      _ <- result.value
      // if result.value is not empty => error
    } yield ()
  }

  def visibleCenter[F[_]](element: ElementHandle)(implicit O: Operations[F]): FreeS[F, (Double, Double)] = for {
    _ <- scrollIntoViewIfNeeded(element)
    box <- boundingBox(element)
  } yield {
    (box.x + box.width / 2, box.y + box.height / 2)
  }

  def boundingBox[F[_]](element: ElementHandle)(implicit O: Operations[F]): FreeS[F, BoundingBox] = for {
    model <- O.domain.dom.getBoxModel(objectId = element.remoteObject.objectId)
  } yield {
    val border = model.border.value.zipWithIndex
    val xs = border.collect { case (x, i) if i % 2 == 0 => x }
    val ys = border.collect { case (y, i) if i % 2 == 1 => y }

    val x = xs.min
    val y = ys.min
    val width = xs.max - x
    val height = ys.max - y

    BoundingBox(x, y, width, height)
  }

  private def remoteObjectToCallArgument(remoteObject: Runtime.RemoteObject): Runtime.CallArgument = {
    remoteObject.objectId match {
      case None => Runtime.CallArgument(value = Some(remoteObject.asJson))
      case Some(objectId) => Runtime.CallArgument(objectId = Some(objectId))
    }
  }
}

final case class BoundingBox(x: Double, y: Double, width: Double, height: Double)
