package org.scalajars

import scalaz._, Scalaz._
import play.api._


package object core {
  def use[A <: Plugin](implicit app: Application, m: Manifest[A]) = {
    app.plugin[A].getOrElse(throw new RuntimeException(m.erasure.toString+ " plugin should be available at this point"))
  }

  type Error = Throwable
  type Res[+A] = Error \/ A

  implicit val ThrowableMonoid = new Monoid[Throwable] {
    def zero = UnknownException
    def append(f1: Throwable, f2: => Throwable) = CombinedException(f1, f2)
  }

  case class Path(parts: IndexedSeq[String]){
    lazy val str = parts.mkString("/")
    lazy val list = parts.toList
    def endsWith(str: String) = parts.lastOption.filter(_.endsWith(str)).isDefined
    def reversed = Path(parts.reverse)
    def base = Path(parts.dropRight(1))
    def baseOption = if(parts.isEmpty) none else some(base)
    def baseAndLast = parts.lastOption.map(l => (base, l))
    def /(s: String) = Path(parts :+ s)
  }

  object Path {
    def apply(str: String): Path = Path(str.split('/').dropWhile(""==).toIndexedSeq)
    val root = Path(Vector.empty[String])
  }
}
