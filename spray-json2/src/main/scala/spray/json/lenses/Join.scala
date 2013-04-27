package spray.json
package lenses

/**
 * This typeclass with its implicit instances decides how two containers should be joined.
 *
 * Supported containers are
 *
 *   - `Id` for scalar values
 *   - `Option` for optional values
 *   - `Seq` for a vector of values
 *
 * Those container types form an ordering from most specific to most abstract:
 *
 *   - `Id` contains always one value
 *   - `Option` contains always zero or one value
 *   - `Seq` can contain any number of values
 *
 * The rule to determine what the result type of joining two container types is that the result
 * is as generic as the more generic of both of the input types.
 *
 * The implicit definitions in the companion object of join form evidence for this ordering.
 */
trait Join[M1[_], M2[_], R[_]] {
  def get(outer: Ops[M1], inner: Ops[M2]): Ops[R]
}

object Join {
  def apply[M1[_], M2[_], R[_]](f: ((Ops[M1], Ops[M2])) ⇒ Ops[R]): Join[M1, M2, R] = new Join[M1, M2, R] {
    def get(outer: Ops[M1], inner: Ops[M2]): Ops[R] = f(outer, inner)
  }

  implicit def joinWithSeq[M2[_]]: Join[Seq, M2, Seq] = Join(_._1)

  implicit def joinWithScalar[M2[_]]: Join[Id, M2, M2] = Join(_._2)

  implicit def joinWithOptionWithId: Join[Option, Id, Option] = Join(_._1)

  implicit def joinOptionWithOption: Join[Option, Option, Option] = Join(_._1)

  implicit def joinOptionWithSeq: Join[Option, Seq, Seq] = Join(_._2)
}