@file:Suppress("NO_ACTUAL_FOR_EXPECT", "PHANTOM_CLASSIFIER", "LEAKING_PHANTOM_TYPE")

package kotlin.ranges

expect infix fun PlatformInt.downTo(to: Byte): PlatformIntProgression
expect infix fun PlatformInt.downTo(to: Short): PlatformIntProgression
expect infix fun PlatformInt.downTo(to: Int): PlatformIntProgression
expect infix fun PlatformInt.downTo(to: Long): LongProgression

expect infix fun PlatformUInt.downTo(to: UByte): PlatformUIntProgression
expect infix fun PlatformUInt.downTo(to: UShort): PlatformUIntProgression
expect infix fun PlatformUInt.downTo(to: UInt): PlatformUIntProgression
expect infix fun PlatformUInt.downTo(to: ULong): ULongProgression

expect infix fun PlatformInt.until(to: PlatformInt): PlatformIntRange
expect infix fun PlatformUInt.until(to: PlatformUInt): PlatformUIntRange
