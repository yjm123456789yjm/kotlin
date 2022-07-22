


fun lolkek(s: String, k: String): String {
//    val x = when (s) {
//        "1234" -> 1234
//        "ARcZguv1234" -> 12340
//        "321" -> 321
//        else -> 777
//    }
    val x = when (s) {
        "1234" -> 1234
        "<>" -> when(k) {
            "567" -> 567
            "ARcZguv567" -> 5670
            "981" -> 981
            else -> 999
        }
        else -> 777
    }

//    val e = 33
//
//    val x = when (e) {
//        1234, 5, 4321 -> 12344
//        33 -> 333
//        else -> 777
//    }

    return "$s $k $x"
}

fun box(): String {

    val results = listOf("1234 567 1234",
                         "1234 ARcZguv567 1234",
                         "1234 981 1234",
                         "1234 ARcZguv981 1234",
                         "1234 999 1234",
                         "ARcZguv1234 567 777",
                         "ARcZguv1234 ARcZguv567 777",
                         "ARcZguv1234 981 777",
                         "ARcZguv1234 ARcZguv981 777",
                         "ARcZguv1234 999 777",
                         "<> 567 567",
                         "<> ARcZguv567 5670",
                         "<> 981 981",
                         "<> ARcZguv981 999",
                         "<> 999 999",
                         "777 567 777",
                         "777 ARcZguv567 777",
                         "777 981 777",
                         "777 ARcZguv981 777",
                         "777 999 777",
    )

    var i = 0
    for (s in listOf("1234", "ARcZguv1234", "<>", "777")) {
        for (k in listOf("567", "ARcZguv567", "981", "ARcZguv981", "999")) {
            val res = lolkek(s, k)
            println(res)
            if (res != results[i]) {
                error("expected $res found ${results[i]}")
            }
            i++
        }
    }

    return "OK"
}