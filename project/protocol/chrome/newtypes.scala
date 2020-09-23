package protocol.chrome

import io.circe.Decoder

sealed trait Deprecated
object Deprecated {
  case object True extends Deprecated
  case object False extends Deprecated

  implicit val decoder: Decoder[Deprecated] = Decoder[Option[Boolean]].map {
    case None | Some(false) => Deprecated.False
    case Some(true) => Deprecated.True
  }
}

sealed trait Experimental
object Experimental {
  case object True extends Experimental
  case object False extends Experimental

  implicit val decoder: Decoder[Experimental] = Decoder[Option[Boolean]].map {
    case None | Some(false) => Experimental.False
    case Some(true) => Experimental.True
  }
}
