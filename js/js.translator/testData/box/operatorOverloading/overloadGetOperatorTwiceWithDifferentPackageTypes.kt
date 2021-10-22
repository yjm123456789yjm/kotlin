// KT-49077

// FILE: api1.kt
package api1

data class Id(val uuid: Int)

// FILE: api2.kt
package api2

data class Id(val uuid: Int)

// FILE: main.kt
class Service {
    operator fun get(id: api1.Id): String {
        return "O"
    }
    operator fun get(id: api2.Id): String {
        return "K"
    }
}

fun box(): String {
    var service = Service()
    return service[api1.Id(6)] + service[api2.Id(12)]
}