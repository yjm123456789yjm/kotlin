/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.common.arguments

import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants.CALL
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants.NO_CALL
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import kotlin.reflect.KType
import kotlin.reflect.typeOf

open class DefaultValues(
    val defaultValue: String,
    val type: KType,
    val kotlinOptionsType: KType,
    val possibleValues: List<String>? = null,
    val fromKotlinOptionConverterProp: String? = null,
    val toKotlinOptionConverterProp: String? = null,
    val toArgumentConverter: String? = toKotlinOptionConverterProp
) {
    open class DefaultBoolean(defaultValue: Boolean) : DefaultValues(defaultValue.toString(), typeOf<Boolean>(), typeOf<Boolean>())

    object BooleanFalseDefault : DefaultBoolean(false)

    object BooleanTrueDefault : DefaultBoolean(true)

    object StringNullDefault : DefaultValues("null", typeOf<String?>(), typeOf<String?>())

    object EmptyStringListDefault : DefaultValues("emptyList<String>()", typeOf<List<String>>(), typeOf<List<String>>())

    object EmptyStringArrayDefault : DefaultValues(
        "emptyList<String>()",
        typeOf<List<String>>(),
        typeOf<List<String>>(),
        toArgumentConverter = ".toTypedArray()"
    )

    object LanguageVersions : DefaultValues(
        "null",
        typeOf<LanguageVersion?>(),
        typeOf<String?>(),
        possibleValues = LanguageVersion.values()
            .filterNot { it.isUnsupported }
            .map { "\"${it.description}\"" },
        fromKotlinOptionConverterProp = """
        if (this != null) ${typeOf<LanguageVersion>()}.fromVersionString(this) else null
        """.trimIndent(),
        toKotlinOptionConverterProp = """
        this?.versionString
        """.trimIndent()
    )

    object ApiVersions : DefaultValues(
        "null",
        typeOf<ApiVersion?>(),
        typeOf<String?>(),
        possibleValues = LanguageVersion.values()
            .map(ApiVersion.Companion::createByLanguageVersion)
            .filterNot { it.isUnsupported }
            .map { "\"${it.description}\"" },
        fromKotlinOptionConverterProp = """
        if (this != null) ${typeOf<ApiVersion>()}.parse(this) else null
        """.trimIndent(),
        toKotlinOptionConverterProp = """
        this?.versionString
        """.trimIndent()
    )

    object JvmTargetVersions : DefaultValues(
        "null",
        typeOf<JvmTarget?>(),
        typeOf<String?>(),
        JvmTarget.supportedValues().map { "\"${it.description}\"" },
        fromKotlinOptionConverterProp = """
        if (this != null) { ${typeOf<JvmTarget>()}.fromString(this) ?: throw ${typeOf<IllegalArgumentException>()}("Unknown JVM target version: ${'$'}this") } else { null }
        """.trimIndent(),
        toKotlinOptionConverterProp = """
        this?.description
        """.trimIndent()
    )

    object LanguageFeaturesToggles : DefaultValues(
        "emptyMap()",
        typeOf<Map<LanguageFeature, Boolean>>(),
        typeOf<Map<LanguageFeature, Boolean>>(),
        listOf("Check 'LanguageFetaure' enum values"),
        //toArgumentConverter = ".
    )

    object JsEcmaVersions : DefaultValues(
        "\"v5\"",
        typeOf<String>(),
        typeOf<String>(),
        listOf("\"v5\"")
    )

    object JsModuleKinds : DefaultValues(
        "\"plain\"",
        typeOf<String>(),
        typeOf<String>(),
        listOf("\"plain\"", "\"amd\"", "\"commonjs\"", "\"umd\"")
    )

    object JsSourceMapContentModes : DefaultValues(
        "null",
        typeOf<String?>(),
        typeOf<String?>(),
        listOf(
            K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER,
            K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS,
            K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING
        ).map { "\"$it\"" }
    )

    object JsMain : DefaultValues(
        "\"" + CALL + "\"",
        typeOf<String>(),
        typeOf<String>(),
        listOf("\"" + CALL + "\"", "\"" + NO_CALL + "\"")
    )
}
