var that: Int
    lateinit field: String
    get() = field.length
    set(value) {
        field = value.toString()
    }

fun test() {
    that = 1
    println(that)
}
