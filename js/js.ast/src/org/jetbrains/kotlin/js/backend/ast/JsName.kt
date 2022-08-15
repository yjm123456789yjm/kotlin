// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package org.jetbrains.kotlin.js.backend.ast

import org.jetbrains.kotlin.js.backend.ast.metadata.HasMetadata
import org.jetbrains.kotlin.js.backend.ast.metadata.HasMetadataImpl
import org.jetbrains.kotlin.js.common.Symbol

/**
 * An abstract base class for named JavaScript objects.
 * @param ident the unmangled ident to use for this name
 */
interface JsName : HasMetadata, Symbol {
    val ident: String

    val isTemporary: Boolean

    fun makeRef() = JsNameRef(this)
}

open class JsNameLegacy(override val ident: String, override val isTemporary: Boolean = false) : JsName, HasMetadataImpl() {
    override fun toString() = ident
}

@JvmInline
value class JsNameIr(override val ident: String) : JsName {
    override val isTemporary: Boolean
        get() = false

    override fun toString() = ident

    override fun <T> getData(key: String): T = error("Metadata is unavailable")
    override fun <T> setData(key: String, value: T) = error("Metadata is unavailable")
    override fun hasData(key: String): Boolean = error("Metadata is unavailable")
    override fun removeData(key: String) = error("Metadata is unavailable")
    override fun copyMetadataFrom(other: HasMetadata) = error("Metadata is unavailable")
}

@JvmInline
value class JsNameIrTemporary(override val ident: String) : JsName {
    override val isTemporary: Boolean
        get() = true

    override fun toString() = ident

    override fun <T> getData(key: String): T = error("Metadata is unavailable")
    override fun <T> setData(key: String, value: T) = error("Metadata is unavailable")
    override fun hasData(key: String): Boolean = error("Metadata is unavailable")
    override fun removeData(key: String) = error("Metadata is unavailable")
    override fun copyMetadataFrom(other: HasMetadata) = error("Metadata is unavailable")
}
