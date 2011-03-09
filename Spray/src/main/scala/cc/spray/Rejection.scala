package cc.spray

trait Rejection

case object MethodRejection extends Rejection

case object AcceptRejection extends Rejection

case object PathMatchedRejection extends Rejection