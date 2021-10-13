// EXPECTED_REACHABLE_NODES: 1281
// CALL_MAIN

var ok: String = "fail"

fun main(args: Array<String>) {
    assert(0 == args.size)

    ok = "OK"
}

fun box() = ok