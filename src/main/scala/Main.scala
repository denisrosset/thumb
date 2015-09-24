package thumbthumb

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.lang.{Runtime, Thread}

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.functional.syntax._

/** Main object, reads the configuration file and instantiates the tasks in separate threads. */
object Main extends App {
  args match {
    case Array(configFile) =>
      println(s"Reading configuration file $configFile")
      val bytes = Files.readAllBytes(Paths.get(configFile))
      val config = Json.parse(bytes).validate[Config].fold(errs => sys.error(errs.toString), identity)
      implicit val log = new Log(true, config.log.map(_.toFile))
      implicit val cw = new CtrlCWatch(println("Graceful shutdown..."), println("Forced shutdown..."))
      // TODO threads
      config.tasks.foreach(TaskRunner(_).run)
    case _ =>
      println("Error: launch Thumbthumb with a .json config file as parameter")
  }
}
