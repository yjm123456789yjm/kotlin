// FILE: SamConstructor.kt
@SamWithReceiver
public interface Sam {
    void run(String a);
}

// FILE: test.kt
annotation class SamWithReceiver

fun test() {
    Sam { a ->
        System.out.println(a)
    }

    Sam {
        val a: String = this
        val a2: String = it
        System.out.println(a)
    }
}