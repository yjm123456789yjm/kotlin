// TARGET_BACKEND: JVM
// LAMBDAS: INDY
// WITH_STDLIB

import java.io.*
import kotlin.jvm.*

fun box(): String {
    var o = ""
    var b = 1.toByte()
    var d = 1.0
    var f = 1.0f
    var i = 1
    var j = 1L
    var s = 1.toShort()
    var c = '1'
    var z = true

    val lambda = @JvmSerializableLambda fun(): String {
        o = "OK"
        b++; d++; f++; i++; j++; s++; c++
        z = false
        return "$o $b $d $f $i $j $s $c $z"
    }

    val baos = ByteArrayOutputStream()
    val oos = ObjectOutputStream(baos)
    oos.writeObject(lambda)
    oos.close()

    val bais = ByteArrayInputStream(baos.toByteArray())
    val ois = ObjectInputStream(bais)
    val result = (ois.readObject() as () -> String)()
    ois.close()

    return if (result == "OK 2 2.0 2.0 2 2 2 2 false") "OK" else "Fail: $result"
}
