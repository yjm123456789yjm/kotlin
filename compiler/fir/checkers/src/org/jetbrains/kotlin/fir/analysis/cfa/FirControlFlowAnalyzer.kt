/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.cfa.util.LocalPropertyAndCapturedWriteCollector
import org.jetbrains.kotlin.fir.analysis.cfa.util.PropertyInitializationInfoCollector
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkersComponent
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph

class FirControlFlowAnalyzer(
    session: FirSession,
    declarationCheckers: DeclarationCheckers = session.checkersComponent.declarationCheckers
) {
    private val cfaCheckers = declarationCheckers.controlFlowAnalyserCheckers
    private val variableAssignmentCheckers = declarationCheckers.variableAssignmentCfaBasedCheckers

    // Currently declaration in analyzeXXX is not used, but it may be useful in future
    @Suppress("UNUSED_PARAMETER")
    fun CheckerContext.analyzeClassInitializer(klass: FirClass, graph: ControlFlowGraph, reporter: DiagnosticReporter) {
        if (graph.owner != null) return
        cfaCheckers.forEach { checker -> with(checker) { analyze(graph, reporter) } }
    }

    @Suppress("UNUSED_PARAMETER")
    fun CheckerContext.analyzeFunction(function: FirFunction, graph: ControlFlowGraph, reporter: DiagnosticReporter) {
        if (graph.owner != null) return

        cfaCheckers.forEach { checker -> with(checker) { analyze(graph, reporter) } }
        if (containingDeclarations.any { it is FirProperty || it is FirFunction }) return
        this.runAssignmentCfaCheckers(graph, reporter)
    }

    @Suppress("UNUSED_PARAMETER")
    fun CheckerContext.analyzePropertyInitializer(property: FirProperty, graph: ControlFlowGraph, reporter: DiagnosticReporter) {
        if (graph.owner != null) return

        cfaCheckers.forEach { checker -> with(checker) { analyze(graph, reporter) } }
        this.runAssignmentCfaCheckers(graph, reporter)
    }

    @Suppress("UNUSED_PARAMETER")
    fun CheckerContext.analyzePropertyAccessor(
        accessor: FirPropertyAccessor,
        graph: ControlFlowGraph,
        reporter: DiagnosticReporter
    ) {
        if (graph.owner != null) return

        cfaCheckers.forEach { checker -> with(checker) { analyze(graph, reporter) } }
        runAssignmentCfaCheckers(graph, reporter)
    }

    private fun CheckerContext.runAssignmentCfaCheckers(graph: ControlFlowGraph, reporter: DiagnosticReporter) {
        val (properties, capturedWrites) = LocalPropertyAndCapturedWriteCollector.collect(graph)
        if (properties.isEmpty()) return
        val data = PropertyInitializationInfoCollector(properties).getData(graph)
        variableAssignmentCheckers.forEach { checker ->
            with(checker) { analyze(graph, reporter, data, properties, capturedWrites) }
        }
    }
}
