// KT-49077
class Foo {
    data class Id(val uuid: Int)
}

class Bar {
    data class Id(val uuid: Int)
}

class Service {
    operator fun get(id: Foo.Id): String {
        return "O"
    }
    operator fun get(id: Bar.Id): String {
        return "K"
    }
}

fun box(): String {
    var service = Service()
    return service[Foo.Id(6)] + service[Bar.Id(12)]
}