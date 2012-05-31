package cc.spray.json.lenses

trait Ops[M[_]] {
    def flatMap[T, U](els: M[T])(f: T => Seq[U]): Seq[U]

    def allRight[T](v: Seq[Validated[T]]): Validated[M[T]]

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