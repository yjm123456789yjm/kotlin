fun Any?.equals(other: Any?): Boolean { // (1)
    if (this == null) return other == null
    return this.equals(other)
}

interface A
interface B

fun test_1(x: A?, y: B) {
    if (x == y) { // (1)
        // x != null
    }

    if (x === y) {
        // x != null
        // x hasType B
        // x has all TS from y
        // y has all TS from x
    }

    if (x != null) {
        // x != null
    }

    if (x !== null) {
        // x != null
    }
}

fun test_2(x: A?, y: B?) {
    if (x == y) { // (1)
        // no info
    }

    if (x === y) {
        // x hasType B?
        // y hasType A?
    }
}

