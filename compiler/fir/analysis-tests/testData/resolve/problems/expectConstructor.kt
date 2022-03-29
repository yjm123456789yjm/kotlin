open class Base(v: String)

expect class Derived<!NO_VALUE_FOR_PARAMETER!>(v: String)<!> : Base

expect open class ExpectBase(v: String)

expect class ExpectDerived<!NO_VALUE_FOR_PARAMETER!>(v: String)<!> : ExpectBase

expect open class IOException(message: String, cause: Throwable?) {
    <!PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED!>constructor(message: String)<!>
}

expect class EOFException<!NONE_APPLICABLE!>(message: String)<!> : IOException
