@JsFun("(a, b) => a + b")
private external fun jsf(a: StringRefString, b: StringRefString): StringRefString


fun lolkek(): Int {

    val a = StringRefString.fromString("lol")
    val b = StringRefString.fromString("kek")

    val sss = jsf(a, b)
    println(sss)

    return 42
}

fun box(): String {
    println(lolkek())
    return "OK"
}