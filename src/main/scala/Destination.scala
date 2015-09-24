package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread}

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Try, Success, Failure}

import com.sksamuel.scrimage._
import com.sksamuel.scrimage.composite._
import com.sksamuel.scrimage.nio._

/** Destination for a task, includes a destination folder, a list of PNG masks
  * used to resize/composite the images. */
case class Destination(taskName: String, config: DestinationConfig) {
  import PathUtils._
  import config.{target => targetPath, masks => maskPath}

  protected def readMasks(): Map[Double, Image] = {
    val matcher = extMatcher(maskPath.getFileSystem, pngExt)
    val masks = enumerateRegularFiles(maskPath, matcher)
      .map( name => Image.fromFile(maskPath.resolve(name).toFile) )
    if (masks.isEmpty)
      sys.error(s"$taskName: Error: $maskPath does not contain masks")
    masks.map(image => image.logRatio -> image).toMap
  }

  protected val writer = JpegWriter(config.jpegCompression, false)

  protected val maskMap = readMasks()

  protected def findClosestMask(image: Image): Image =
    maskMap(maskMap.closestKey(image.logRatio))

  protected def writeImage(image: Image, outPath: Path): Unit =
    image.output(outPath)(writer)

  def process(image: Image, filename: Path)(implicit log: Log): Unit = {
    val outPath = config.target.resolve(filename)
    if (!Files.isRegularFile(outPath)) {
      log(s"$taskName: $filename -> ${config.target}")
      val mask = findClosestMask(image)
      val result = image.cover(mask.width, mask.height).composite(AlphaComposite(1f), mask)
      writeImage(result, outPath)
    } else
      log(s"$taskName: warning: file $filename already exists in ${config.target}")
  }
}
