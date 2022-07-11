import kotlinx.cinterop.*
import dummy.X
import yummy.*

fun commonMain() {
  val x = cValue<X> {
    n = 3
  }
  yummy2(x)
}
