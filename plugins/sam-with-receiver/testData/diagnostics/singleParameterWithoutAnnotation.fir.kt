// FILE: SamConstructor.kt
public interface Sam {
    void run(String a);
}

// FILE: test.kt
fun test() {
    Sam { a ->
        System.out.println(a)
    }

    Sam {
        val a = this
        System.out.println(a)
    }
}