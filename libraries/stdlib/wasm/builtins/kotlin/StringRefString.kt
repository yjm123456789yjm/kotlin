/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

import kotlin.wasm.internal.*
import kotlin.wasm.internal.reftypes.*

public open class StringRefCharSequence internal constructor(internal val referenceView: stringview_wtf16): CharSequence {
    public override val length: Int get() =
        wasm_stringview_wtf16_length(referenceView)

    public override fun get(index: Int): Char =
        wasm_stringview_wtf16_get_codeunit(referenceView, index).toChar()

    public override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        val length = wasm_stringview_wtf16_length(referenceView)
        val actualStartIndex = startIndex.coerceAtLeast(0)
        val actualEndIndex = endIndex.coerceAtMost(length)
        return StringRefCharSequence(
            wasm_string_as_wtf16(
                wasm_stringview_wtf16_slice(referenceView, actualStartIndex, actualEndIndex)
            )
        )
    }
}

public class StringRefString internal constructor(internal val reference: stringref) :
    Comparable<StringRefString>,
    StringRefCharSequence(wasm_string_as_wtf16(reference))
{
    companion object {
        fun fromString(string: String): StringRefString {
            val chars = string.chars
            return StringRefString(wasm_string_new_wtf16_array(chars, 0, chars.len()))
        }
    }

    public operator fun plus(other: Any?): StringRefString {
        val otherReference = when (other) {
            is StringRefString -> other.reference
            else -> fromString(other.toString()).reference
        }
        return StringRefString(wasm_string_concat(reference, otherReference))
    }

    public override fun compareTo(other: StringRefString): Int {
        val thisIterator = wasm_string_as_iter(this.reference)
        val otherIterator = wasm_string_as_iter(other.reference)

        var thisCode = wasm_stringview_iter_next(thisIterator)
        var otherCode = wasm_stringview_iter_next(otherIterator)
        while (thisCode != -1 && otherCode != -1) {
            val diff = thisCode - otherCode
            if (diff != 0) return diff
            thisCode = wasm_stringview_iter_next(thisIterator)
            otherCode = wasm_stringview_iter_next(otherIterator)
        }
        return if (thisCode == -1 && otherCode == -1) 0 else this.length - other.length
    }

    public override fun equals(other: Any?): Boolean =
        other != null &&
        other is StringRefString &&
        wasm_string_eq(reference, other.reference)

    public override fun toString(): String {
        val ref = reference
        val size = wasm_string_measure_wtf16(ref)
        val array = WasmCharArray(size)
        wasm_string_encode_wtf16_array(ref, array, 0)
        return String(array)
    }

    public override fun hashCode(): Int {
        if (_hashCode != 0) return _hashCode

        val iter = wasm_string_as_iter(reference)

        var codePoint = wasm_stringview_iter_next(iter)
        var hash = 0
        while (codePoint != -1) {
            hash = 31 * hash + codePoint
            codePoint = wasm_stringview_iter_next(iter)
        }
        _hashCode = hash
        return _hashCode
    }
}
