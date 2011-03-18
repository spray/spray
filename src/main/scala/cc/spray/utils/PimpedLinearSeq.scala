package cc.spray.utils

import collection.immutable.LinearSeq

class PimpedLinearSeq[+A](val underlying: LinearSeq[A]) {
  
  def mapFind[B](f: A => Option[B]): Option[B] = {
    var res: Option[B] = None
    var these = underlying
    while (res.isEmpty && !these.isEmpty) {
      res = f(these.head)
      these = these.tail
    }
    res
  }
  
}