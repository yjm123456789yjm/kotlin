// LANGUAGE: +DataObjects


data object Override {
    override fun equals(other: Any?): Boolean {
        return true
    }

    override fun hashCode(): Int {
        return 1
    }
}

data object NoOverride {
    fun equals(other: Any?, tag: Int): Boolean {
        return true
    }

    fun hashCode(param: String): Int {
        return 1
    }
}

open class Super {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return 1
    }
}

data object OverridenInSuper: Super() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}