import java.nio.file._

import play.api.libs.json._
import play.api.libs.functional.syntax._

package object thumbthumb {
  implicit val pathReader: Reads[Path] = new Reads[Path] {
    def reads(json: JsValue): JsResult[Path] = json.validate[String].flatMap { str =>
      Option(Paths.get(str)) match {
        case Some(path) => JsSuccess(path)
        case None => JsError(s"Invalid path: $str")
      }
    }
  }
}
