package thumbthumb

import java.io._
import java.nio.file._
import java.text.SimpleDateFormat
import java.util.Date

/** Helper class to display log messages, and append them in a log file.
  * 
  * Is passed around methods as an implicit, to avoid boilerplate. */
class Log(display: Boolean = true, logfile: Option[File] = None) {
  val format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
  def apply(string: String): Unit = {
    if (display)
      println(string)
    logfile.foreach { file =>
      val curDate = new Date
      val fw = new FileWriter(file, true)
      fw.write(format.format(curDate) + " " + string + "\r\n")
      fw.close
    }
  }
}
