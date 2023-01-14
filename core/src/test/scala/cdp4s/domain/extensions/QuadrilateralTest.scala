package cdp4s.domain.extensions

import zio.test.ZIOSpecDefault
import zio.test.assertCompletes

object QuadrilateralTest extends ZIOSpecDefault {

  override val spec = suite("Quadrilateral")(
    test("from valid DOM.Quad data") {
      assertCompletes
    },
  )
}
