// EXPECTED_REACHABLE_NODES: 1296
// CALL_MAIN

import kotlin.coroutines.*

var ok: String = "fail"

var callback: () -> Unit = {}

suspend fun main(args: Array<String>) {
    assert(0 == args.size)

    suspendCoroutine<Unit> { cont ->
        callback = {
            cont.resume(Unit)
        }
    }

    ok = "OK"
}

fun box(): String {
    assert("fail" == ok)
    callback()
    return ok
}