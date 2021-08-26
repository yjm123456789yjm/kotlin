/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.core

import org.jetbrains.kotlin.commonizer.cir.CirClassType
import org.jetbrains.kotlin.commonizer.cir.CirEntityId

private val kotlinByte = CirEntityId.create("kotlin/Byte")
private val kotlinShort = CirEntityId.create("kotlin/Short")
private val kotlinInt = CirEntityId.create("kotlin/Int")
private val kotlinLong = CirEntityId.create("kotlin/Long")

private val kotlinUByte = CirEntityId.create("kotlin/UByte")
private val kotlinUShort = CirEntityId.create("kotlin/UShort")
private val kotlinUInt = CirEntityId.create("kotlin/UInt")
private val kotlinULong = CirEntityId.create("kotlin/ULong")

object AliasedNumbersTypeCommonizer : AssociativeCommonizer<CirClassType> {
    override fun commonize(first: CirClassType, second: CirClassType): CirClassType? {
        when (first.classifierId) {
            kotlinByte -> when (second.classifierId) {
                kotlinShort, kotlinInt, kotlinLong -> return second
            }
            kotlinShort -> when (second.classifierId) {
                kotlinByte -> return first
                kotlinInt, kotlinLong -> return second
            }
            kotlinInt -> when (second.classifierId) {
                kotlinByte, kotlinShort -> return first
                kotlinLong -> return second
            }
            kotlinLong -> when (second.classifierId) {
                kotlinByte, kotlinShort, kotlinInt -> return first
            }
            kotlinUByte -> when (second.classifierId) {
                kotlinUShort, kotlinUInt, kotlinULong -> return second
            }
            kotlinUShort -> when (second.classifierId) {
                kotlinUByte -> return first
                kotlinUInt, kotlinULong -> return second
            }
            kotlinUInt -> when (second.classifierId) {
                kotlinUByte, kotlinUShort -> return first
                kotlinULong -> return second
            }
            kotlinULong -> when (second.classifierId) {
                kotlinUByte, kotlinUInt, kotlinUShort -> return first
            }

            else -> return null
        }

        return null
    }
}