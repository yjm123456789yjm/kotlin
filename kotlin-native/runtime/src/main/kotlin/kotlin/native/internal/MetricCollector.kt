/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.native.internal

object MetricCollector {
    @GCUnsafeCall("Kotlin_MetricCollector_zoneStart")
    external fun zoneStart(name: String): Unit

    @GCUnsafeCall("Kotlin_MetricCollector_zoneEnd")
    external fun zoneEnd(name: String): Unit
}
