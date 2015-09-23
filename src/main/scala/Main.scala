package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread}

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Try, Success, Failure}

import scala.collection.mutable.{Map => MutableMap, PriorityQueue}

import com.sksamuel.scrimage._
import com.sksamuel.scrimage.composite._
import com.sksamuel.scrimage.nio._

case class ToTreat(filename: Path, priority: Int, creationTime: Long) extends Ordered[ToTreat] {
  def compare(that: ToTreat) = priority.compare(that.priority)
}

case class Destination(taskName: String, config: DestinationConfig) {
  import PathUtils._
  import config.{target => targetPath, masks => maskPath}
  val matcher = extMatcher(maskPath.getFileSystem, pngExt)
  val masks = enumerateRegularFiles(maskPath, matcher).map(name => Image.fromFile(maskPath.resolve(name).toFile))
  val writer = JpegWriter(config.jpegCompression, false)

  def logRatio(image: Image) = math.log10(image.width.toDouble / image.height.toDouble)
  val maskMap = MutableMap.empty[Double, Image] ++ masks.map(image => logRatio(image) -> image)
  def findClosestMask(image: Image): Image = {
    val imageRatio = logRatio(image)
    val maskRatio = maskMap.keys.minBy(r => math.abs(r - imageRatio))
    maskMap(maskRatio)
  }
  def process(source: Path, filename: Path)(implicit log: Log): Unit = {
    val outPath = config.target.resolve(filename)
    if (!Files.isRegularFile(outPath)) {
      val fullPath = source.resolve(filename)
      log(s"$taskName: $fullPath -> ${config.target}")
      val image = Image.fromFile(fullPath.toFile)
      val mask = findClosestMask(image)
      val res = image.cover(mask.width, mask.height).composite(AlphaComposite(1f), mask)
      res.output(outPath)(writer)
    } else
      log(s"$taskName: warning: file $filename already exists in ${config.target}")
  }
}

case class TaskRunner(config: TaskConfig) {
  import PathUtils._
  import config.{name => taskName, source, timeWriteMilli, blockSize, destinations => destinationConfigs}
  val destinations = destinationConfigs.map(Destination(taskName, _))
  val fileSystem = source.getFileSystem
  val matcher = extMatcher(fileSystem, jpegExt)

  val creationTime: MutableMap[Path, Long] = MutableMap.empty[Path, Long]
  val toTreat: PriorityQueue[ToTreat] = PriorityQueue.empty[ToTreat]
  var lastPriority = 0

  def enqueueExisting(implicit log: Log): Unit = {
    val existingFiles = enumerateRegularFiles(config.source, matcher)
    toTreat ++= existingFiles.map { path =>
      (path, Files.getLastModifiedTime(path).toMillis) } // add modif. timestamp
      .sortBy(_._2) // sort by ascending modification time
      .zipWithIndex // ordering by modification time
      .map {
      case ((path, time), index) => ToTreat(config.source.relativize(path), lastPriority + index, time) // use index as priority, and mod. time as creation time
    }
    lastPriority += existingFiles.size
    log(s"$taskName: in the folder, ${existingFiles.size} existing files have been enqueued")
  }

  def processEvents(watchService: WatchService)(implicit log: Log): Unit = Option(watchService.poll).foreach { watchKey =>
    import StandardWatchEventKinds._
    val events = watchKey.pollEvents()
    events.foreach { event =>
      event.kind match {
        case OVERFLOW =>
          log(s"$taskName: warning: some filesystem events have been lost. Restart software to rescan all files")
        case ENTRY_MODIFY | ENTRY_CREATE | ENTRY_DELETE =>
          val eventPath = event.context.asInstanceOf[Path]
          if (matcher.matches(eventPath)) {
            val now = java.lang.System.currentTimeMillis
            event.kind match {
              case ENTRY_MODIFY =>
                if (now - creationTime.getOrElse(eventPath, 0L) > timeWriteMilli)
                  log(s"$taskName: Warning: file $eventPath in $source has been modified, but the modification is not propagated")
              case ENTRY_CREATE =>

                // TODO: filter already created files

                creationTime(eventPath) = now
                toTreat += ToTreat(eventPath, lastPriority, now)
                lastPriority += 1
                log(s"$taskName: File $eventPath created in $source")
              case ENTRY_DELETE =>
                log(s"$taskName: Warning: file $eventPath in $source has been deleted, but the deletion is not propagated")
            }
          }
      }
    }
    if(!watchKey.reset()) {
      log("$taskName: Error with key reset")
    }
  }

  def dequeueMostUrgent: Seq[ToTreat] = {
    var n = blockSize
    val b = Seq.newBuilder[ToTreat]
    while (toTreat.nonEmpty && n > 0) {
      b += toTreat.dequeue
      n -= 1
    }
    b.result
  }

  def process(tt: ToTreat)(implicit log: Log): Unit = {
    destinations.foreach(_.process(source, tt.filename))
  }

  def processInParallel(toProcess: Seq[ToTreat])(implicit log: Log): Unit = {
    val pp = toProcess.par
    import scala.collection.parallel.ForkJoinTaskSupport
    import scala.concurrent.forkjoin.ForkJoinPool
    pp.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(config.threads))
    pp.foreach(process)
  }

  def run(implicit cw: CtrlCWatch, log: Log): Unit = {
    enqueueExisting
    val watchService: WatchService = fileSystem.newWatchService
    val watchKey: WatchKey = source.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    while (cw) {
      // enqueue new created files 
      processEvents(watchService)
      // get the most urgent files (number is given by config.blockSize)
      val mostUrgent = dequeueMostUrgent

       // the files that are too recent are reenqueued to avoid processing partially written files
      val now = java.lang.System.currentTimeMillis
      val (reenqueue, toProcess) = mostUrgent.partition(tt => now - tt.creationTime < timeWriteMilli)
      toTreat ++= reenqueue

      if (toProcess.nonEmpty) {
        processInParallel(toProcess)
      } else Thread.sleep(config.idleWaitMilli) // if no new files, sleep for a while
    }
    println("Finished gracefully task")
  }
}

object Main extends App {
  args match {
    case Array(configFile) =>
      println(s"Reading configuration file $configFile")
      val bytes = Files.readAllBytes(Paths.get(configFile))
      val config = Json.parse(bytes).validate[Config].fold(errs => sys.error(errs.toString), identity)
      implicit val log = new Log(true, config.log.map(_.toFile))
      implicit val cw = new CtrlCWatch(println("Graceful shutdown..."), println("Forced shutdown..."))
      config.tasks.foreach(TaskRunner(_).run)
    case _ =>
      println("Error: launch Thumbthumb with a .json config file as parameter")
  }
}
