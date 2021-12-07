@file:Suppress(
    "DEPRECATED_BINARY_MOD",
    "NO_ACTUAL_FOR_EXPECT",
    "PHANTOM_CLASSIFIER",
    "LEAKING_PHANTOM_TYPE",
    "LEAKING_PHANTOM_TYPE_IN_SUPERTYPES"
)

package kotlin

expect value class PlatformUInt internal constructor(internal val data: PlatformInt) : Comparable<PlatformUInt> {

    companion object {
        val MAX_VALUE: PlatformUInt
        val MIN_VALUE: PlatformUInt
        val SIZE_BITS: Int
        val SIZE_BYTES: Int
    }

    inline infix fun and(other: PlatformUInt): PlatformUInt

    inline operator fun compareTo(other: UByte): Int
    inline operator fun compareTo(other: UShort): Int

    inline operator fun div(other: UShort): PlatformUInt
    inline operator fun div(other: ULong): ULong
    inline operator fun div(other: UByte): PlatformUInt
    inline operator fun div(other: UInt): PlatformUInt

    inline fun inv(): PlatformUInt

    inline operator fun minus(other: UByte): PlatformUInt
    inline operator fun minus(other: ULong): ULong
    inline operator fun minus(other: UInt): PlatformUInt
    inline operator fun minus(other: UShort): PlatformUInt

    inline infix fun or(other: PlatformUInt): PlatformUInt

    inline operator fun plus(other: ULong): ULong
    inline operator fun plus(other: UByte): PlatformUInt
    inline operator fun plus(other: UShort): PlatformUInt
    inline operator fun plus(other: UInt): PlatformUInt

    inline operator fun rem(other: UShort): PlatformUInt
    inline operator fun rem(other: ULong): ULong
    inline operator fun rem(other: UInt): PlatformUInt
    inline operator fun rem(other: UByte): PlatformUInt

    inline infix fun shl(bitCount: Int): PlatformUInt
    inline infix fun shr(bitCount: Int): PlatformUInt

    inline operator fun times(other: UByte): PlatformUInt
    inline operator fun times(other: UInt): PlatformUInt
    inline operator fun times(other: ULong): ULong
    inline operator fun times(other: UShort): PlatformUInt

    inline fun toByte(): Byte
    inline fun toDouble(): Double
    inline fun toFloat(): Float
    inline fun toInt(): Int
    inline fun toLong(): Long
    inline fun toShort(): Short
    inline fun toUByte(): UByte
    inline fun toUInt(): UInt
    inline fun toULong(): ULong
    inline fun toUShort(): UShort

    inline infix fun xor(other: PlatformUInt): PlatformUInt

    inline fun floorDiv(other: UByte): PlatformUInt
    inline fun floorDiv(other: UShort): PlatformUInt
    inline fun floorDiv(other: UInt): PlatformUInt
    inline fun floorDiv(other: ULong): ULong

    operator fun mod(other: UByte): UByte
    operator fun mod(other: UShort): UShort
    operator fun mod(other: UInt): UInt
    operator fun mod(other: ULong): ULong
}
