/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskExecutionResults
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskLoggers
import org.jetbrains.kotlin.gradle.plugin.stat.ReportStatistics
import org.jetbrains.kotlin.gradle.plugin.statistics.KotlinBuildEsStatListener
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToBuildScan
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToElasticSearch
import org.jetbrains.kotlin.gradle.report.configureReporting

abstract class NewKotlinGradleBuildServices : BuildService<BuildServiceParameters.None>, AutoCloseable {

    private val log = Logging.getLogger(this.javaClass)
    private val CLASS_NAME = NewKotlinGradleBuildServices::class.java.simpleName
    val INIT_MESSAGE = "Initialized $CLASS_NAME"
    val DISPOSE_MESSAGE = "Disposed $CLASS_NAME"

    init {
        log.kotlinDebug(INIT_MESSAGE)

        TaskLoggers.clear()
        TaskExecutionResults.clear()
    }

    override fun close() {
        log.kotlinDebug(DISPOSE_MESSAGE)
        TaskLoggers.clear()
        TaskExecutionResults.clear()
    }

    companion object {

        fun registerIfAbsent(project: Project): Provider<NewKotlinGradleBuildServices> = project.gradle.sharedServices.registerIfAbsent(
            "kotlin-build-service-${NewKotlinGradleBuildServices::class.java.canonicalName}_${NewKotlinGradleBuildServices::class.java.classLoader.hashCode()}",
            NewKotlinGradleBuildServices::class.java
        ) {
            configureReporting(project.gradle)
            addListeners(project)
        }

        fun addListeners(project: Project) {
            val kotlinGradleEsListenerProvider = project.provider {
                val listeners = project.rootProject.objects.listProperty(ReportStatistics::class.java)
                    .value(listOf<ReportStatistics>(ReportStatisticsToElasticSearch))
                if (project.gradle.startParameter.isBuildScan) {
                    project.rootProject.extensions.findByName("buildScan")
                        ?.also { listeners.add(ReportStatisticsToBuildScan(it as BuildScanExtension)) }
                }
                KotlinBuildEsStatListener(project.rootProject.name, listeners.get())
            }

            val listenerRegistryHolder = BuildEventsListenerRegistryHolder.getInstance(project)

            listenerRegistryHolder.listenerRegistry.onTaskCompletion(kotlinGradleEsListenerProvider)

            val buildFinishProvider = project.provider {
                KotlinGradleBuildOperationListener(
                    project.rootProject.buildDir,
                    project.rootProject.rootDir,
                    PropertiesProvider(project).kotlinGradleMemoryUsage
                )
            }

            listenerRegistryHolder.listenerRegistry.onTaskCompletion(buildFinishProvider)
        }

    }
}