package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread}

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Try, Success, Failure}

object PathUtils {
  /** JPEG file extensions */
  val jpegExt = Seq("JPG", "JPEG", "jpg", "jpeg")
  /** PNG file extensions */
  val pngExt = Seq("PNG", "png")

  /** Builds a matcher for the given extensions. */
  def extMatcher(fileSystem: FileSystem, extensions: Iterable[String]): PathMatcher = {
    val pattern = "glob:**." + extensions.mkString("{",",","}")
    fileSystem.getPathMatcher(pattern)
  }

  /** Enumerates files matching a pattern in a folder. */
  def enumerateRegularFiles(directory: Path, matcher: PathMatcher): Seq[Path] = {
    assert(Files.isDirectory(directory))
    val filter = new DirectoryStream.Filter[Path] {
      def accept(path: Path): Boolean =
        Files.isRegularFile(path) && matcher.matches(path)
    }
    Files.newDirectoryStream(directory, filter).toIndexedSeq
  }
}
