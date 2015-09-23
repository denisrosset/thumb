package thumbthumb

import java.nio.file._

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class DestinationConfig(target: Path, masks: Path, jpegCompression: Int)
case class TaskConfig(name: String, source: Path, timeWriteMilli: Int, blockSize: Int, threads: Int, idleWaitMilli: Int, destinations: Seq[DestinationConfig])
case class Config(log: Option[Path], tasks: Seq[TaskConfig])

object DestinationConfig {
  implicit val reader: Reads[DestinationConfig] = Json.reads[DestinationConfig]
}

object TaskConfig {
  implicit val reader: Reads[TaskConfig] = Json.reads[TaskConfig]
}

object Config {
  implicit val reader: Reads[Config] = Json.reads[Config]
}
