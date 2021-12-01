/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.caches

import com.intellij.openapi.application.ApplicationManager

internal inline fun <reified T : Any> service(): T {
    val serviceClass = T::class.java
    return ApplicationManager.getApplication().getService(serviceClass)
        ?: throw RuntimeException("Cannot find service ${serviceClass.name} (classloader=${serviceClass.classLoader})")
}

internal inline fun <reified T : Any> serviceOrNull(): T? = ApplicationManager.getApplication().getService(T::class.java)
