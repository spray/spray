package spray.json.lenses

/**
 * A trait to define common operations for different container types.
 * There's some bias towards `Seq` because container types have to support
 * conversions towards and from `Seq`.
 *
 * This could probably made more general but the methods defined here comprise
 * exactly the set of operations needed to allow combining different kinds of
 * lenses.
 */
trait Ops[M[_]] {
  def flatMap[T, U](els: M[T])(f: T => Seq[U]): Seq[U]

  /**
   * Checks if all the elements of the Seq are valid and then
   * packages them into a validated container.
   */
  def allRight[T](v: Seq[Validated[T]]): Validated[M[T]]

  /**
   * Converts a validated container of `T`s into a sequence
   * of validated values.
   */
  def toSeq[T](v: Validated[M[T]]): Seq[Validated[T]]

  def map[T, U](els: M[T])(f: T => U): Seq[U] =
    flatMap(els)(v => Seq(f(v)))
}

object Ops {
  implicit def idOps: Ops[Id] = new Ops[Id] {
    def flatMap[T, U](els: T)(f: T => Seq[U]): Seq[U] = f(els)

    def allRight[T](v: Seq[Validated[T]]): Validated[T] = v.head

    def toSeq[T](v: Validated[T]): Seq[Validated[T]] = Seq(v)
  }

  implicit def optionOps: Ops[Option] = new Ops[Option] {
    def flatMap[T, U](els: Option[T])(f: T => Seq[U]): Seq[U] =
      els.toSeq.flatMap(f)

    def allRight[T](v: Seq[Validated[T]]): Validated[Option[T]] =
      v match {
        case Nil => Right(None)
        case Seq(Right(x)) => Right(Some(x))
        case Seq(Left(e)) => Left(e)
      }

    def toSeq[T](v: Validated[Option[T]]): Seq[Validated[T]] = v match {
      case Right(Some(x)) => Seq(Right(x))
      case Right(None) => Nil
      case Left(e) => Seq(Left(e))
    }
  }

  implicit def seqOps: Ops[Seq] = new Ops[Seq] {
    def flatMap[T, U](els: Seq[T])(f: T => Seq[U]): Seq[U] =
      els.flatMap(f)

    def allRight[T](v: Seq[Validated[T]]): Validated[Seq[T]] = {
      def inner(l: List[Validated[T]]): Validated[List[T]] = l match {
        case head :: tail =>
          for {
            headM <- head
            tailM <- inner(tail)
          } yield headM :: tailM
        case Nil =>
          Right(Nil)
      }
      inner(v.toList)
    }

    def toSeq[T](x: Validated[Seq[T]]): Seq[Validated[T]] = x match {
      case Right(x) => x.map(Right(_))
      case Left(e) => List(Left(e))
    }
  }
}