// FILE: SamConstructor.kt
@SamWithReceiver
public interface Sam {
    String run(String a, String b);
}

// FILE: test.kt
annotation class SamWithReceiver

fun test() {
    Sam { a, b ->
        System.out.println(a)
        ""
    }

    Sam { b ->
        val a: String = this
        System.out.println(a)
        ""
    }
}