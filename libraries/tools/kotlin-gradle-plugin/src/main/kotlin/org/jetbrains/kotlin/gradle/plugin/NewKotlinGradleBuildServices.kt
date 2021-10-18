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
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskExecutionResults
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskLoggers
import org.jetbrains.kotlin.gradle.plugin.stat.ReportStatistics
import org.jetbrains.kotlin.gradle.plugin.statistics.KotlinBuildEsStatListener
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToBuildScan
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToElasticSearch
import org.jetbrains.kotlin.gradle.report.configureReporting
import java.io.File

abstract class NewKotlinGradleBuildServices : BuildService<NewKotlinGradleBuildServices.Parameters>, AutoCloseable,
    OperationCompletionListener {

    interface Parameters : BuildServiceParameters {
        var buildDir: File
        var rootDir: File
    }

    private val log = Logging.getLogger(this.javaClass)
    private var buildHandler: KotlinGradleFinishBuildHandler = KotlinGradleFinishBuildHandler()
    private val CLASS_NAME = NewKotlinGradleBuildServices::class.java.simpleName
    val INIT_MESSAGE = "Initialized $CLASS_NAME"
    val DISPOSE_MESSAGE = "Disposed $CLASS_NAME"
    val CLOSE_MESSAGE = "Close $CLASS_NAME"

    init {
        log.kotlinDebug(INIT_MESSAGE)
        println(INIT_MESSAGE)
    }

    override fun onFinish(event: FinishEvent?) {
        buildHandler.buildFinished(parameters.buildDir, parameters.rootDir)
        log.kotlinDebug(DISPOSE_MESSAGE)
        println(DISPOSE_MESSAGE)

        TaskLoggers.clear()
        TaskExecutionResults.clear()
    }

    override fun close() {
        log.kotlinDebug(CLOSE_MESSAGE)
        println(CLOSE_MESSAGE)
    }

    companion object {

        fun registerIfAbsent(project: Project): Provider<NewKotlinGradleBuildServices> = project.gradle.sharedServices.registerIfAbsent(
            "kotlin-build-service-${NewKotlinGradleBuildServices::class.java.canonicalName}_${NewKotlinGradleBuildServices::class.java.classLoader.hashCode()}",
            NewKotlinGradleBuildServices::class.java
        ) { service ->
            configureReporting(project.gradle)
            service.parameters.rootDir = project.rootProject.rootDir
            service.parameters.buildDir = project.rootProject.buildDir

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

            listenerRegistryHolder.listenerRegistry!!.onTaskCompletion(kotlinGradleEsListenerProvider)
        }

    }
}