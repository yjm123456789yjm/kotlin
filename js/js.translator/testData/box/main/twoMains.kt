// EXPECTED_REACHABLE_NODES: 1281
// CALL_MAIN

var o: String = "fail O"
var k: String = "K"

fun main() {
    k = "fail K"
}

fun main(args: Array<String>) {
    assert(args.size == 0)

    o = "O"
}

fun box() = o + k