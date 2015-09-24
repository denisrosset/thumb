package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread}

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MutableMap, PriorityQueue}

import com.sksamuel.scrimage._

/** Processes a task, while the program has not been interrupted by CTRL+C.
  * 
  * Only works with the .jpeg/jpg/JPEG/JPG files in the source directory of the task.
  * 
  * Performs the following steps:
  * 
  * 1. Builds the `Destination` objects, reading the associated PNG masks
  * 2. Reads the existing files in the source directory, enqueue them with a low priority
  * 3. Registers a watch service on the source directory, to be notified of file creation
  * 4. Loop while CTRL+C is not observed:
  * 4.1. Watch the newly created files, enqueue them with the highest priority
  * 4.2. Show a warning message if a file is modified/deleted, but do not enqueue it
  * 4.3. Dequeue the highest `n = batchSize` images and process them in parallel towards all the destinations
  *
  */
case class TaskRunner(config: TaskConfig) {
  import PathUtils._
  import config.{name => taskName, source, timeWriteMilli, batchSize, destinations => destinationConfigs}

  /** Values used often */
  protected val fileSystem = source.getFileSystem
  protected val matcher = extMatcher(fileSystem, jpegExt)

  protected def makeDestinations(): Iterable[Destination] = destinationConfigs.map(Destination(taskName, _))

  /** Destinations where to put the resized/composite images, along with the masks to be used. */
  protected val destinations = makeDestinations

  /** Creation (or last mod. if creation was not watched) timestamp for each filename in the source directory. */
  protected  val creationTime: MutableMap[Path, Long] = MutableMap.empty[Path, Long]
  /** Priority queue of images to process. */
  protected  val toTreat: PriorityQueue[ToTreat] = PriorityQueue.empty[ToTreat]
  /** Last priority used, is incremented for each image. */
  protected  var lastPriority = 0

  protected def enqueueExisting()(implicit log: Log): Unit = {
    val existingFiles = enumerateRegularFiles(config.source, matcher)
    val withTimestamp = existingFiles
      .map( p => (config.source.relativize(p), Files.getLastModifiedTime(p).toMillis) )
    creationTime ++= withTimestamp
    toTreat ++= withTimestamp
      .sortBy(_._2) // sort by ascending modification time
      .zipWithIndex // ordering by modification time
                    // use index as priority, and mod. time as creation time
      .map { case ((path, time), index) => ToTreat(path, lastPriority + index, time) }
    lastPriority += existingFiles.size
    log(s"$taskName: in the folder, ${existingFiles.size} existing files have been enqueued")
  }

  protected def processEvents(watchService: WatchService)(implicit log: Log): Unit = Option(watchService.poll).foreach { watchKey =>
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
                if (now - creationTime.getOrElse(eventPath, now) > timeWriteMilli)
                  log(s"$taskName: Warning: file $eventPath in $source has been recreated, but the modification is not propagated")
                else {
                  creationTime(eventPath) = now
                  toTreat += ToTreat(eventPath, lastPriority, now)
                  lastPriority += 1
                  log(s"$taskName: File $eventPath created in $source")
                }
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

  protected def process(tt: ToTreat)(implicit log: Log): Unit = {
    val image = Image.fromFile(source.resolve(tt.filename).toFile)
    destinations.foreach(_.process(image, tt.filename))
  }

  protected def processInParallel(toProcess: Seq[ToTreat])(implicit log: Log): Unit = {
    val pp = toProcess.par
    import scala.collection.parallel.ForkJoinTaskSupport
    import scala.concurrent.forkjoin.ForkJoinPool
    pp.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(config.threads))
    pp.foreach(process)
  }

  def run(implicit cw: CtrlCWatch, log: Log): Unit = {
    enqueueExisting()
    val watchService: WatchService = fileSystem.newWatchService
    val watchKey: WatchKey = source.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    while (cw) { // while CTRL+C is not pressed
      // enqueue new created files 
      processEvents(watchService)
      // get the most urgent files (number is given by config.batchSize)
      val mostUrgent = toTreat.dequeueN(batchSize)

       // the files that are too recent are reenqueued to avoid processing partially written files
      val now = java.lang.System.currentTimeMillis
      val (reenqueue, toProcess) = mostUrgent.partition(tt => now - tt.creationTime < timeWriteMilli)
      toTreat ++= reenqueue

      if (toProcess.nonEmpty) {
        processInParallel(toProcess)
      } else Thread.sleep(config.idleWaitMilli) // if no new files, sleep for a while
    }
    watchService.close
    println(s"$taskName: finished gracefully")
  }
}
