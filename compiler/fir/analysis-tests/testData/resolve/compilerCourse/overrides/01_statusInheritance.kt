abstract class A {
    private open fun privateFun() {}
    protected open fun protectedFun() {}

    open operator fun plus(other: A): A = this
    open infix fun infixFun(other: A) {}

    abstract suspend fun suspendFun()
}

class B : A() {
    override fun privateFun() {} // <--- incorrect, nothing to override
    override /*protected*/ fun protectedFun() {}

    override /*operator*/ fun plus(other: A): A = this
    override /*infix*/ fun infixFun(other: A) {}

    override fun suspendFun() {} // <--- missing suspend
}


/*
 * B.plus overrides A.plus ->
 * 1. A.plus is overridden of B.plus
 * 2. B.plus is override of A.plus
 */
