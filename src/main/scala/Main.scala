package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread, Runnable}

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.functional.syntax._

/** Main object, reads the configuration file and instantiates the tasks in separate threads. */
object Main extends App {

  args match {
    case Array(configFile) =>
      println(s"Reading configuration file $configFile")
      val config = Config.read(Paths.get(configFile))
      implicit val log = new Log(true, config.log.map(_.toFile))
      implicit val cw = new CtrlCWatch(println("Graceful shutdown..."), println("Forced shutdown..."))
      val threads = config.tasks.map { taskConfig =>
        val runnable = new Runnable {
          def run = TaskRunner(taskConfig).run
        }
        new Thread(runnable)
      }
      threads.foreach(_.start)
      threads.foreach(_.join)
      log("All tasks finished gracefully, exiting.")
    case _ =>
      println("Error: launch Thumbthumb with a .json config file as parameter")
  }
}
