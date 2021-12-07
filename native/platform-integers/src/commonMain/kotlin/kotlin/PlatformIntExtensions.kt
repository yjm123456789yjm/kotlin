@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "NO_ACTUAL_FOR_EXPECT", "PHANTOM_CLASSIFIER", "LEAKING_PHANTOM_TYPE")

package kotlin

// override modifier is needed on one unknown of them, can't be member because of that
expect operator fun PlatformInt.compareTo(other: Int): Int
expect operator fun PlatformInt.compareTo(other: Long): Int

expect operator fun PlatformInt.div(other: PlatformInt): PlatformInt
expect operator fun PlatformInt.minus(other: PlatformInt): PlatformInt
expect operator fun PlatformInt.plus(other: PlatformInt): PlatformInt
expect operator fun PlatformInt.rangeTo(other: PlatformInt): PlatformIntRange
expect operator fun PlatformInt.rem(other: PlatformInt): PlatformInt
expect operator fun PlatformInt.times(other: PlatformInt): PlatformInt

expect fun PlatformInt.toUByte(): UByte
expect fun PlatformInt.toUShort(): UShort
expect fun PlatformInt.toUInt(): UInt
expect fun PlatformInt.toULong(): ULong

// Class members for UInt
expect inline fun PlatformInt.floorDiv(other: Byte): PlatformInt
expect inline fun PlatformInt.floorDiv(other: Short): PlatformInt
expect inline fun PlatformInt.floorDiv(other: Int): PlatformInt
expect inline fun PlatformInt.floorDiv(other: Long): Long

// Class members for UInt
expect fun PlatformInt.mod(other: Byte): Byte
expect fun PlatformInt.mod(other: Short): Short
expect fun PlatformInt.mod(other: Int): Int
expect fun PlatformInt.mod(other: Long): Long

expect fun PlatformInt.countLeadingZeroBits(): Int
expect fun PlatformInt.countOneBits(): Int
expect fun PlatformInt.countTrailingZeroBits(): Int

@ExperimentalStdlibApi
expect fun PlatformInt.rotateLeft(bitCount: Int): PlatformInt
@ExperimentalStdlibApi
expect fun PlatformInt.rotateRight(bitCount: Int): PlatformInt

expect fun PlatformInt.takeHighestOneBit(): PlatformInt
expect fun PlatformInt.takeLowestOneBit(): PlatformInt

// Mixed plain/platform extensions
expect operator fun Byte.div(other: PlatformInt): PlatformInt
expect operator fun Short.div(other: PlatformInt): PlatformInt
expect operator fun Int.div(other: PlatformInt): PlatformInt
expect operator fun Long.div(other: PlatformInt): PlatformInt

expect operator fun Byte.minus(other: PlatformInt): PlatformInt
expect operator fun Short.minus(other: PlatformInt): PlatformInt
expect operator fun Int.minus(other: PlatformInt): PlatformInt
expect operator fun Long.minus(other: PlatformInt): PlatformInt

expect operator fun Byte.plus(other: PlatformInt): PlatformInt
expect operator fun Short.plus(other: PlatformInt): PlatformInt
expect operator fun Int.plus(other: PlatformInt): PlatformInt
expect operator fun Long.plus(other: PlatformInt): PlatformInt

expect operator fun Byte.rangeTo(other: PlatformInt): PlatformIntRange
expect operator fun Short.rangeTo(other: PlatformInt): PlatformIntRange
expect operator fun Int.rangeTo(other: PlatformInt): PlatformIntRange
expect operator fun Long.rangeTo(other: PlatformInt): PlatformIntRange

expect operator fun Byte.rem(other: PlatformInt): PlatformInt
expect operator fun Short.rem(other: PlatformInt): PlatformInt
expect operator fun Int.rem(other: PlatformInt): PlatformInt
expect operator fun Long.rem(other: PlatformInt): PlatformInt

expect operator fun Byte.times(other: PlatformInt): PlatformInt
expect operator fun Short.times(other: PlatformInt): PlatformInt
expect operator fun Int.times(other: PlatformInt): PlatformInt
expect operator fun Long.times(other: PlatformInt): PlatformInt

expect operator fun Byte.compareTo(other: PlatformInt): Int
expect operator fun Short.compareTo(other: PlatformInt): Int
expect operator fun Int.compareTo(other: PlatformInt): Int
expect operator fun Long.compareTo(other: PlatformInt): Int
