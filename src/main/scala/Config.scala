package thumbthumb

import java.nio.file._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import JsonHelpers.absolutePathReader
case class DestinationConfig(
  target: Path, /** Target folder where to put the resized/composited images */
  masks: Path, /** Folder where the PNG masks are located */
  jpegCompression: Int /** JPEG compression ratio to use */
)

/** Task configuration */
case class TaskConfig(
  name: String, /** Task name */
  source: Path, /** Folder watched over */
  timeWriteMilli: Int, /** Time (in ms) to wait after file creation, after which the image is processed */
  batchSize: Int, /** Number of images to process at the same time, should be > 4 * threads */
  threads: Int, /** Number of threads to use */
  idleWaitMilli: Int, /** Waiting time (in ms) between two filesystem polls */
  destinations: Seq[DestinationConfig] /** Sequence of destinations */ 
)

/** Overall program configuration, composed of a logfile path and a sequence of tasks. */
case class Config(log: Option[Path], tasks: Seq[TaskConfig])

// below: use the default JSON mappings (Play JSON inception macros) to read the config file
object DestinationConfig {
  implicit val reader: Reads[DestinationConfig] = Json.reads[DestinationConfig]
}

object TaskConfig {
  implicit val reader: Reads[TaskConfig] = Json.reads[TaskConfig]
}

object Config {
  implicit val reader: Reads[Config] = Json.reads[Config]
  def read(path: Path): Config = {
    val bytes = Files.readAllBytes(path)
    Json.parse(bytes).validate[Config].fold(errs => sys.error(errs.toString), identity)
  }
}
