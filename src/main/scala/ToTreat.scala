package thumbthumb

import java.nio.file._

/** Describes a file to be processed, along with its priority (highest is most urgent),
  * creation time (in UTC ms timestamp). */
case class ToTreat(filename: Path, priority: Int, creationTime: Long) extends Ordered[ToTreat] {
  def compare(that: ToTreat) = priority.compare(that.priority)
}
