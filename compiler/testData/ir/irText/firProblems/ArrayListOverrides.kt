// FULL_JDK

class A1 : java.util.ArrayList<String>()

class A2 : java.util.ArrayList<String>() {
    override fun remove(x: String): Boolean = true
}
