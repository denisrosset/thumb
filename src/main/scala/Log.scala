package thumbthumb

import java.io._
import java.nio.file._
import java.text.SimpleDateFormat
import java.util.Date

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
