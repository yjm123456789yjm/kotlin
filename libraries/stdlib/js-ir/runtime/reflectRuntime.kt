/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js

import kotlin.reflect.KProperty

internal fun getPropertyCallableRef(
    name: String,
    paramCount: Int,
    superTypes: IntArray,
    getter: dynamic,
    setter: dynamic
): KProperty<*> {
    getter.get = getter
    getter.set = setter
    getter.callableName = name
    return getPropertyRefClass(
        getter,
        getKPropMetadata(paramCount, setter),
        getInterfaceMaskFor(getter, superTypes)
    ).unsafeCast<KProperty<*>>()
}

internal fun getLocalDelegateReference(name: String, superTypes: IntArray, mutable: Boolean, lambda: dynamic): KProperty<*> {
    lambda.get = lambda
    lambda.set = if (mutable) lambda else null
    lambda.callableName = name
    return getPropertyRefClass(
        lambda,
        getKPropMetadata(0, lambda),
        getInterfaceMaskFor(lambda, superTypes)
    ).unsafeCast<KProperty<*>>()
}

private fun getPropertyRefClass(obj: Ctor, metadata: Metadata, imask: BitMask?): dynamic {
    obj.`$metadata$` = metadata;
    obj.constructor = obj;
    imask?.let { obj.`$imask$` = it }
    return obj;
}

private fun getInterfaceMaskFor(obj: Ctor, superTypes: IntArray): BitMask? {
    val alreadyCreatedMask = obj.`$imask$`.unsafeCast<BitMask?>()

    if (alreadyCreatedMask != null || superTypes.size == 0) {
        return alreadyCreatedMask
    }

    return BitMask(*superTypes)
}

@Suppress("UNUSED_PARAMETER")
private fun getKPropMetadata(paramCount: Int, setter: Any?): dynamic {
    return propertyRefClassMetadataCache[paramCount][if (setter == null) 0 else 1]
}

private fun metadataObject(): Metadata {
    val undef = js("undefined")
    return classMeta(undef, undef, undef, undef)
}

private val propertyRefClassMetadataCache: Array<Array<dynamic>> = arrayOf<Array<dynamic>>(
    //                 immutable     ,     mutable
    arrayOf<dynamic>(metadataObject(), metadataObject()), // 0
    arrayOf<dynamic>(metadataObject(), metadataObject()), // 1
    arrayOf<dynamic>(metadataObject(), metadataObject())  // 2
)

