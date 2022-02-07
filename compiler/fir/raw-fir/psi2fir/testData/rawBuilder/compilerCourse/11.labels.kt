fun test_1(b1: Boolean, b2: Boolean): Int {
    run {
        if (b1) return 10

        if (b2) return@run "hello"

        "world"
    }
}

fun test_2(cond: Boolean?): Int {
    run l@{
        cond.let m@{
            when (it) {
                true -> return@l
                false -> return@m
                null -> return 10
            }

        }
    }
}
