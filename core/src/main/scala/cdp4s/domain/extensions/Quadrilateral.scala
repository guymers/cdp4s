package cdp4s.domain.extensions

import cats.syntax.traverse._
import cdp4s.domain.model.DOM

final case class Quadrilateral(
  a: Quadrilateral.Point,
  b: Quadrilateral.Point,
  c: Quadrilateral.Point,
  d: Quadrilateral.Point
) {
  private val points = Array(a, b, c, d)

  // https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/JSHandle.js#L486
  def area: Double = {
    // Compute sum of all directed areas of adjacent triangles
    // https://en.wikipedia.org/wiki/Polygon#Simple_polygons
    val pointsWithNext = points.zip(points.drop(1) ++ points.take(1))
    pointsWithNext.map { case (p1, p2) =>
      (p1.x * p2.y - p2.x * p1.y) / 2
    }.sum.abs
  }

  def middle: Quadrilateral.Point = {
    val (x, y) = points.foldLeft((0D, 0D)) { case ((x, y), p) =>
      (x + p.x, y + p.y)
    }
    Quadrilateral.Point(x / 4, y / 4)
  }
}

object Quadrilateral {
  final case class Point(x: Double, y: Double)

  def fromDomQuad(quad: DOM.Quad): Option[Quadrilateral] = {
    val parts = quad.value.grouped(2).toList.traverse { points =>
      for {
        x <- points.headOption
        y <- points.drop(1).headOption
      } yield Point(x, y)
    }
    parts.collect {
      case a :: b :: c :: d :: Nil => Quadrilateral(a, b, c, d)
    }
  }
}
