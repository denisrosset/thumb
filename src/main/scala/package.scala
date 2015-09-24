import java.nio.file._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import com.sksamuel.scrimage.Image

import scala.collection.mutable.{Map => MutableMap, PriorityQueue}

package object thumbthumb {
  object JsonHelpers {
    /** Reads a JSON string as a java Path, makes it absolute */
    implicit val absolutePathReader: Reads[Path] = new Reads[Path] {
      def reads(json: JsValue): JsResult[Path] = json.validate[String].flatMap { str =>
        Option(Paths.get(str)) match {
          case Some(path) => JsSuccess(path.toAbsolutePath)
          case None => JsError(s"Invalid path: $str")
        }
      }
    }
  }

  /** Enrichment helper methods */

  implicit class RichImage(val image: Image) extends AnyVal {
    /** Image ratio encoded as logarithm, for landscape/portrait invariant closest ratio matching. */
    def logRatio = math.log10(image.width.toDouble / image.height.toDouble)
  }

  implicit class RichMap[V](val map: Map[Double, V]) extends AnyVal {
    /** Finds the key closest to `k`, assuming the map is not empty. */
    def closestKey(k: Double): Double = map.keys.minBy(k1 => math.abs(k1 - k))
  }
  
  implicit class RichPriorityQueue[A](val queue: PriorityQueue[A]) extends AnyVal {
    /** Dequeues the `n` most urgent elements (or all elements if `queue.size < n`. */
    def dequeueN(n: Int): Seq[A] = {
      var j = n
      val b = Seq.newBuilder[A]
      while (queue.nonEmpty && j > 0) {
        b += queue.dequeue
        j -= 1
      }
      b.result
    }
  }
}
