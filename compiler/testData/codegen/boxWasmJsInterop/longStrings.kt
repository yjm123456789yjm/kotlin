


fun lolkek(s: String, k: String) {
    val x = when (s) {
        "1234" -> 1234
        "ARcZguv1234" -> 12340
        "321" -> 321
        else -> 777
    }
//    val x = when (s) {
//        "1234" -> 1234
//        "<>" -> when(k) {
//            "567" -> 567
//            "ARcZguv567" -> 5670
//            "981" -> 981
//            else -> 999
//        }
//        else -> 777
//    }
    println("$s $k $x")
}

fun box(): String {

    for (s in listOf("1234", "ARcZguv1234", "<>", "777")) {
        for (k in listOf("567", "ARcZguv567", "981", "ARcZguv981", "999")) {
            lolkek(s, k)
        }
    }




    return "OK"
}