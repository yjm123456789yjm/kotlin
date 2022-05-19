/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.services

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.target.buildDistribution
import java.io.File

/**
 * Service for [PlatformManager].
 *
 * To get this service, you can use [PlatformManagerService.from] by giving [Project] to it.
 * It can also be passed to [WorkAction][org.gradle.workers.WorkAction] via [WorkParameters][org.gradle.workers.WorkParameters]
 * as [Property]<[PlatformManagerService]>.
 */
class PlatformManagerService private constructor(private val key: Serializable) : java.io.Serializable {
    val platformManager = PlatformManager(buildDistribution(key.konanHome.absolutePath), key.experimentalDistribution)

    companion object {
        /**
         * Get [PlatformManagerService] for [Project].
         */
        fun from(project: Project): Provider<PlatformManagerService> =
                project.gradle.sharedServices.registerIfAbsent("platformManager", SharedService::class.java) {
                    val nativeProject = project.project(":kotlin-native")
                    val konanHome = nativeProject.projectDir
                    val experimentalDistribution = nativeProject.hasProperty("org.jetbrains.kotlin.native.experimentalTargets")
                    val service = PlatformManagerService(Serializable(konanHome, experimentalDistribution))
                    this.parameters.service.set(service)
                }.flatMap {
                    it.parameters.service
                }
    }

    private fun writeReplace(): Any = key

    private data class Serializable(
            val konanHome: File,
            val experimentalDistribution: Boolean,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 0L
        }

        private fun readResolve(): Any = PlatformManagerService(this)
    }
}

private abstract class SharedService : BuildService<SharedService.Parameters> {
    interface Parameters : BuildServiceParameters {
        val service: Property<PlatformManagerService>
    }
}