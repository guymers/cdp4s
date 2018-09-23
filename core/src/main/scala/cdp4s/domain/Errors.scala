package cdp4s.domain

import freestyle.free._

@free trait Errors {

  def handle[T](fs: FreeS[Operations.Op, T], f: PartialFunction[Throwable, T]): FS[T]

  def handleWith[T](fs: FreeS[Operations.Op, T], f: PartialFunction[Throwable, FreeS[Operations.Op, T]]): FS[T]
}
