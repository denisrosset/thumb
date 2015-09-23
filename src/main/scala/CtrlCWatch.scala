package thumbthumb

import sun.misc.{Signal, SignalHandler}
import SignalHandler.{SIG_DFL, SIG_IGN}

/** Class enabling the capture of the INT signal = CTRL-C keypress; the first CTRL-C
  * toggles the interruption flag that should be watched by all loops in the application;
  * the second CTRL-C call the original signal handler which will shut down the application.
  */
class CtrlCWatch(onFirst: => Unit, onSubsequent: => Unit) {
  object Handler extends SignalHandler {
    def handle(signal: Signal): Unit = {
      if (!interruptFlag) {
        onFirst
        interruptFlag = true
      } else {
        onSubsequent
        if (oldHandler != SIG_DFL && oldHandler != SIG_IGN)
          oldHandler.handle(signal)
      }
      interruptFlag = true
    }
  }
  private var interruptFlag = false
  private val oldHandler: SignalHandler = Signal.handle(new Signal("INT"), Handler)
  def nonInterrupted: Boolean = !interruptFlag
  def interrupted: Boolean = interruptFlag
}

object CtrlCWatch {
  implicit def toBoolean(cw: CtrlCWatch): Boolean = cw.nonInterrupted
}
