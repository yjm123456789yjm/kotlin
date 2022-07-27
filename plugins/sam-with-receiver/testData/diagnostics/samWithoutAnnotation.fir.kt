// FILE: SamConstructor.kt
public interface Sam {
    String run(String a, String b);
}

// FILE: test.kt
fun test() {
    Sam { a, b ->
        System.out.println(a)
        ""
    }

    Sam { b ->
        val a = this@Sam
        System.out.println(a)
        ""
    }
}