/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js

private const val INT_SIZE = 32

internal fun implement(vararg interfaces: dynamic): BitMask {
    var maxSize = 1
    val masks = js("[]")

    for (i in interfaces) {
        var currentSize = maxSize
        val imask: BitMask? = i.prototype.`$imask$` ?: i.`$imask$`

        if (imask != null) {
            masks.push(imask)
            currentSize = imask.intArray.size
        }

        val iid: Int? = i.`$metadata$`.iid
        val iidImask: BitMask? = iid?.let { BitMask(intArrayOf(it)) }

        if (iidImask != null) {
            masks.push(iidImask)
            currentSize = JsMath.max(currentSize, iidImask.intArray.size)
        }

        if (currentSize > maxSize) {
            maxSize = currentSize
        }
    }

    val resultIntArray = IntArray(maxSize) { i ->
        masks.reduce({ acc: Int, it: BitMask ->
            if (i >= it.intArray.size)
                acc
            else
                acc or it.intArray[i]
        }, 0)
    }

    val result = BitMask(IntArray(0))
    result.intArray = resultIntArray
    return result
}

internal class BitMask(activeBits: IntArray) {
    internal var intArray: IntArray = IntArray(0)

    init {
        if (activeBits.size > 0) {
            intArray = IntArray(JsMath.max(*activeBits) / INT_SIZE + 1)
            for (activeBit in activeBits) {
                val numberIndex = activeBit / INT_SIZE
                val positionInNumber = activeBit % INT_SIZE
                val numberWithSettledBit = 1 shl positionInNumber
                intArray[numberIndex] = intArray[numberIndex] or numberWithSettledBit
            }
        }
    }

    fun isBitSettled(possibleActiveBit: Int): Boolean {
        val numberIndex = possibleActiveBit / INT_SIZE
        if (numberIndex > intArray.size) return false
        val positionInNumber = possibleActiveBit % INT_SIZE
        val numberWithSettledBit = 1 shl positionInNumber
        return intArray[numberIndex] and numberWithSettledBit != 0
    }
}