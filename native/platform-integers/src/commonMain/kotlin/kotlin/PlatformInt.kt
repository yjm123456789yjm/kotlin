@file:Suppress("NO_ACTUAL_FOR_EXPECT", "PHANTOM_CLASSIFIER", "LEAKING_PHANTOM_TYPE", "LEAKING_PHANTOM_TYPE_IN_SUPERTYPES")

package kotlin

expect class PlatformInt private constructor() : Number, Comparable<PlatformInt> {
    companion object {
        val MAX_VALUE: PlatformInt
        val MIN_VALUE: PlatformInt
        val SIZE_BITS: Int
        val SIZE_BYTES: Int
    }

    external infix fun and(other: PlatformInt): PlatformInt
    external fun inv(): PlatformInt
    external infix fun or(other: PlatformInt): PlatformInt

    inline operator fun compareTo(other: Byte): Int
    inline operator fun compareTo(other: Double): Int
    inline operator fun compareTo(other: Float): Int
    inline operator fun compareTo(other: Short): Int

    external operator fun dec(): PlatformInt

    inline operator fun div(other: Byte): PlatformInt
    inline operator fun div(other: Double): Double
    inline operator fun div(other: Float): Float
    operator fun div(other: Long): Long
    operator fun div(other: Int): PlatformInt
    inline operator fun div(other: Short): PlatformInt

    fun equals(other: PlatformInt): Boolean

    external operator fun inc(): PlatformInt

    inline operator fun minus(other: Byte): PlatformInt
    inline operator fun minus(other: Double): Double
    inline operator fun minus(other: Float): Float
    operator fun minus(other: Int): PlatformInt
    operator fun minus(other: Long): Long
    inline operator fun minus(other: Short): PlatformInt

    inline operator fun plus(other: Byte): PlatformInt
    inline operator fun plus(other: Double): Double
    inline operator fun plus(other: Float): Float
    operator fun plus(other: Int): PlatformInt
    operator fun plus(other: Long): Long
    inline operator fun plus(other: Short): PlatformInt

    operator fun rangeTo(other: Byte): PlatformIntRange
    operator fun rangeTo(other: Int): PlatformIntRange
    operator fun rangeTo(other: Long): LongRange
    operator fun rangeTo(other: Short): PlatformIntRange

    inline operator fun rem(other: Byte): PlatformInt
    inline operator fun rem(other: Double): Double
    inline operator fun rem(other: Float): Float
    operator fun rem(other: Int): PlatformInt
    operator fun rem(other: Long): Long
    inline operator fun rem(other: Short): PlatformInt

    external infix fun shl(bitCount: Int): PlatformInt
    external infix fun shr(bitCount: Int): PlatformInt
    external infix fun ushr(bitCount: Int): PlatformInt
    external infix fun xor(other: PlatformInt): PlatformInt

    inline operator fun times(other: Byte): PlatformInt
    inline operator fun times(other: Double): Double
    inline operator fun times(other: Float): Float
    operator fun times(other: Int): PlatformInt
    operator fun times(other: Long): Long
    inline operator fun times(other: Short): PlatformInt

    operator fun unaryMinus(): PlatformInt
    operator fun unaryPlus(): PlatformInt
}
