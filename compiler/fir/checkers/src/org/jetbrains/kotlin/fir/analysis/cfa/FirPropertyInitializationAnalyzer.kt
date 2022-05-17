/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeRevisited
import org.jetbrains.kotlin.contracts.description.isDefinitelyVisited
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwarePropertyInitializationInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.TraverseDirection
import org.jetbrains.kotlin.fir.analysis.cfa.util.traverse
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.declarations.utils.referredPropertySymbol
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

@OptIn(SymbolInternals::class)
object FirPropertyInitializationAnalyzer : AbstractFirPropertyInitializationChecker() {
    override fun analyze(
        graph: ControlFlowGraph,
        reporter: DiagnosticReporter,
        data: Map<CFGNode<*>, PathAwarePropertyInitializationInfo>,
        properties: Set<FirPropertySymbol>,
        capturedWrites: Set<FirVariableAssignment>,
        context: CheckerContext
    ) {
        val reporterVisitor = PropertyReporter(data, properties, capturedWrites, reporter, context)
        graph.traverse(TraverseDirection.Forward, reporterVisitor)
    }

    private class PropertyReporter(
        val data: Map<CFGNode<*>, PathAwarePropertyInitializationInfo>,
        val properties: Set<FirPropertySymbol>,
        val capturedWrites: Set<FirVariableAssignment>,
        val reporter: DiagnosticReporter,
        val context: CheckerContext
    ) : ControlFlowGraphVisitorVoid() {
        override fun visitNode(node: CFGNode<*>) {}

        override fun visitVariableAssignmentNode(node: VariableAssignmentNode) {
            val symbol = getPropertySymbol(node) ?: return
            if (!symbol.fir.isVal) return

            if (node.fir in capturedWrites) {
                val error = if (symbol.fir.isLocal) FirErrors.CAPTURED_VAL_INITIALIZATION else FirErrors.CAPTURED_MEMBER_VAL_INITIALIZATION
                reporter.reportOn(node.fir.lValue.source, error, symbol, context)
                return
            }

            val pathAwareInfo = data.getValue(node)
            for (label in pathAwareInfo.keys) {
                val info = pathAwareInfo[label]!!
                val kind = info[symbol] ?: EventOccurrencesRange.ZERO
                if (symbol.hasInitializer || kind.canBeRevisited()) {
                    reporter.reportOn(node.fir.lValue.source, FirErrors.VAL_REASSIGNMENT, symbol, context)
                    return
                }
            }
        }

        override fun visitQualifiedAccessNode(node: QualifiedAccessNode) {
            val symbol = getPropertySymbol(node) ?: return
            if (symbol.fir.isLateInit || !symbol.isLocal || symbol.hasInitializer) return
            val pathAwareInfo = data.getValue(node)
            for (info in pathAwareInfo.values) {
                val kind = info[symbol] ?: EventOccurrencesRange.ZERO
                if (!kind.isDefinitelyVisited()) {
                    reporter.reportOn(node.fir.source, FirErrors.UNINITIALIZED_VARIABLE, symbol, context)
                    return
                }
            }
        }

        private fun getPropertySymbol(node: CFGNode<*>): FirPropertySymbol? {
            return (node.fir as? FirQualifiedAccess)?.referredPropertySymbol.takeIf { it in properties }
        }
    }
}
