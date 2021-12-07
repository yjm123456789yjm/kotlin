@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "NO_ACTUAL_FOR_EXPECT", "PHANTOM_CLASSIFIER", "LEAKING_PHANTOM_TYPE")

package kotlin

// override modifier is needed on one unknown of them, can't be member because of that
expect operator fun PlatformUInt.compareTo(other: UInt): Int
expect operator fun PlatformUInt.compareTo(other: ULong): Int

expect operator fun PlatformUInt.div(other: PlatformUInt): PlatformUInt
expect operator fun PlatformUInt.minus(other: PlatformUInt): PlatformUInt
expect operator fun PlatformUInt.plus(other: PlatformUInt): PlatformUInt
expect operator fun PlatformUInt.rangeTo(other: PlatformUInt): PlatformUIntRange
expect operator fun PlatformUInt.rem(other: PlatformUInt): PlatformUInt
expect operator fun PlatformUInt.times(other: PlatformUInt): PlatformUInt

expect fun PlatformUInt.countLeadingZeroBits(): Int
expect fun PlatformUInt.countOneBits(): Int
expect fun PlatformUInt.countTrailingZeroBits(): Int

expect fun PlatformUInt.rotateLeft(bitCount: Int): PlatformUInt
expect fun PlatformUInt.rotateRight(bitCount: Int): PlatformUInt

expect fun PlatformUInt.takeHighestOneBit(): PlatformUInt
expect fun PlatformUInt.takeLowestOneBit(): PlatformUInt

// Mixed plain/platform extensions
expect operator fun UByte.div(other: PlatformUInt): PlatformUInt
expect operator fun UShort.div(other: PlatformUInt): PlatformUInt
expect operator fun UInt.div(other: PlatformUInt): PlatformUInt
expect operator fun ULong.div(other: PlatformUInt): PlatformUInt

expect operator fun UByte.minus(other: PlatformUInt): PlatformUInt
expect operator fun UShort.minus(other: PlatformUInt): PlatformUInt
expect operator fun UInt.minus(other: PlatformUInt): PlatformUInt
expect operator fun ULong.minus(other: PlatformUInt): PlatformUInt

expect operator fun UByte.plus(other: PlatformUInt): PlatformUInt
expect operator fun UShort.plus(other: PlatformUInt): PlatformUInt
expect operator fun UInt.plus(other: PlatformUInt): PlatformUInt
expect operator fun ULong.plus(other: PlatformUInt): PlatformUInt

expect operator fun UByte.rangeTo(other: PlatformUInt): PlatformUIntRange
expect operator fun UShort.rangeTo(other: PlatformUInt): PlatformUIntRange
expect operator fun UInt.rangeTo(other: PlatformUInt): PlatformUIntRange
expect operator fun ULong.rangeTo(other: PlatformUInt): PlatformUIntRange

expect operator fun UByte.rem(other: PlatformUInt): PlatformUInt
expect operator fun UShort.rem(other: PlatformUInt): PlatformUInt
expect operator fun UInt.rem(other: PlatformUInt): PlatformUInt
expect operator fun ULong.rem(other: PlatformUInt): PlatformUInt

expect operator fun UByte.times(other: PlatformUInt): PlatformUInt
expect operator fun UShort.times(other: PlatformUInt): PlatformUInt
expect operator fun UInt.times(other: PlatformUInt): PlatformUInt
expect operator fun ULong.times(other: PlatformUInt): PlatformUInt

expect operator fun UByte.compareTo(other: PlatformUInt): Int
expect operator fun UShort.compareTo(other: PlatformUInt): Int
expect operator fun UInt.compareTo(other: PlatformUInt): Int
expect operator fun ULong.compareTo(other: PlatformUInt): Int
