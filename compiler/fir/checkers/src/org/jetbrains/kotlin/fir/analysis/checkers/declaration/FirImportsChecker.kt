/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirDeprecationChecker.reportDeprecationIfNeeded
import org.jetbrains.kotlin.fir.declarations.fullyExpandedClass
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isOperator
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.addToStdlib.filterIsInstanceWithChecker

object FirImportsChecker : FirFileChecker() {
    override fun CheckerContext.check(declaration: FirFile, reporter: DiagnosticReporter) {
        declaration.imports.forEach { import ->
            if (import is FirErrorImport) return@forEach
            if (import.isAllUnder) {
                if (import !is FirResolvedImport) {
                    checkAllUnderFromEnumEntry(import, reporter)
                }
            } else {
                checkCanBeImported(import, reporter)
                if (import is FirResolvedImport) {
                    checkOperatorRename(import, reporter)
                }
            }
            checkDeprecatedImport(import, this, reporter)
        }
        checkConflictingImports(declaration.imports, reporter)
    }

    private fun CheckerContext.checkAllUnderFromEnumEntry(import: FirImport, reporter: DiagnosticReporter) {
        val fqName = import.importedFqName ?: return
        if (fqName.isRoot || fqName.parent().isRoot) return
        val classId = ClassId.topLevel(fqName.parent())
        val classSymbol = classId.resolveToClass(this) ?: return
        if (classSymbol.isEnumClass && classSymbol.collectEnumEntries().any { it.callableId.callableName == fqName.shortName() }) {
            reporter.reportOn(import.source, FirErrors.CANNOT_ALL_UNDER_IMPORT_FROM_SINGLETON, classSymbol.classId.shortClassName)
        }
    }

    private fun CheckerContext.checkCanBeImported(import: FirImport, reporter: DiagnosticReporter) {
        val importedFqName = import.importedFqName ?: return
        val importedName = importedFqName.shortName()
        //empty name come from LT in some erroneous cases
        if (importedName.isSpecial || importedName.identifier.isEmpty()) return

        val symbolProvider = session.symbolProvider
        val parentClassId = (import as? FirResolvedImport)?.resolvedParentClassId
        if (parentClassId != null) {
            val parentClassSymbol = parentClassId.resolveToClass(this) ?: return

            when (val status = parentClassSymbol.getImportStatusOfCallableMembers(this, importedName)) {
                ImportStatus.OK -> return
                else -> {
                    val classId = parentClassSymbol.classId.createNestedClassId(importedName)
                    if (symbolProvider.getClassLikeSymbolByClassId(classId) != null) return
                    if (status == ImportStatus.UNRESOLVED) {
                        reporter.reportOn(import.source, FirErrors.UNRESOLVED_IMPORT, importedName.asString())
                    } else {
                        reporter.reportOn(import.source, FirErrors.CANNOT_BE_IMPORTED, importedName)
                    }
                }
            }
            return
        }
        when {
            ClassId.topLevel(importedFqName).resolveToClass(this) != null -> return
            // Note: two checks below are both heavyweight, so we should do them lazily!
            symbolProvider.getTopLevelCallableSymbols(importedFqName.parent(), importedName).isNotEmpty() -> return
            symbolProvider.getPackage(importedFqName) != null -> reporter.reportOn(
                import.source,
                FirErrors.PACKAGE_CANNOT_BE_IMPORTED
            )
            else -> reporter.reportOn(
                import.source,
                FirErrors.UNRESOLVED_IMPORT,
                importedName.asString()
            )
        }
    }

    private fun CheckerContext.checkConflictingImports(imports: List<FirImport>, reporter: DiagnosticReporter) {
        val interestingImports = imports
            .filterIsInstanceWithChecker<FirResolvedImport> { import ->
                !import.isAllUnder &&
                        import.importedName?.identifierOrNullIfSpecial?.isNotEmpty() == true &&
                        import.resolvesToClass(this)
            }
        interestingImports
            .groupBy { it.aliasName ?: it.importedName!! }
            .values
            .filter { it.size > 1 }
            .forEach { conflicts ->
                conflicts.forEach {
                    reporter.reportOn(it.source, FirErrors.CONFLICTING_IMPORT, it.importedName!!)
                }
            }
    }

    private fun CheckerContext.checkOperatorRename(import: FirResolvedImport, reporter: DiagnosticReporter) {
        val alias = import.aliasName ?: return
        val importedName = import.importedName ?: return
        if (!OperatorConventions.isConventionName(alias)) return
        val classId = import.resolvedParentClassId
        val illegalRename = if (classId != null) {
            val classFir = classId.resolveToClass(this) ?: return
            classFir.classKind.isSingleton && classFir.hasFunction(this, importedName) { it.isOperator }
        } else {
            session.symbolProvider.getTopLevelFunctionSymbols(import.packageFqName, importedName).any {
                it.isOperator
            }
        }
        if (illegalRename) {
            reporter.reportOn(import.source, FirErrors.OPERATOR_RENAMED_ON_IMPORT)
        }
    }

    private fun FirResolvedImport.resolvesToClass(context: CheckerContext): Boolean {
        if (resolvedParentClassId != null) {
            if (isAllUnder) return true
            val parentClass = resolvedParentClassId!!
            val relativeClassName = this.relativeParentClassName ?: return false
            val importedName = this.importedName ?: return false
            val innerClassId = ClassId(parentClass.packageFqName, relativeClassName.child(importedName), false)
            return innerClassId.resolveToClass(context) != null
        } else {
            val importedFqName = importedFqName ?: return false
            if (importedFqName.isRoot) return false
            val importedClassId = ClassId.topLevel(importedFqName)
            return importedClassId.resolveToClass(context) != null
        }
    }

    private fun ClassId.resolveToClass(context: CheckerContext): FirRegularClassSymbol? {
        val classSymbol = context.session.symbolProvider.getClassLikeSymbolByClassId(this) ?: return null
        return when (classSymbol) {
            is FirRegularClassSymbol -> classSymbol
            is FirTypeAliasSymbol -> classSymbol.fullyExpandedClass(context.session)
            is FirAnonymousObjectSymbol -> null
        }
    }

    private fun FirRegularClassSymbol.hasFunction(
        context: CheckerContext,
        name: Name,
        predicate: (FirNamedFunctionSymbol) -> Boolean
    ): Boolean {
        var result = false
        context.session.declaredMemberScope(this).processFunctionsByName(name) { sym ->
            if (!result) {
                result = predicate(sym)
            }
        }
        return result
    }

    private enum class ImportStatus {
        OK,
        CANNOT_BE_IMPORTED,
        UNRESOLVED
    }

    @OptIn(SymbolInternals::class)
    private fun FirRegularClassSymbol.getImportStatusOfCallableMembers(context: CheckerContext, name: Name): ImportStatus {
        return if (classKind.isSingleton) {
            getImportStatusOfCallableMembersFromSingleton(context, name)
        } else {
            getImportStatusOfCallableMembersFromNonSingleton(context, name)
        }
    }

    private fun FirRegularClassSymbol.getImportStatusOfCallableMembersFromSingleton(
        context: CheckerContext,
        name: Name,
    ): ImportStatus {
        // Use declaredMemberScope first because it's faster and it's relatively rare to import members declared from super types.
        for (scope in listOf(context.session.declaredMemberScope(this), unsubstitutedScope(context))) {
            var found = false
            scope.processFunctionsByName(name) {
                found = true
            }
            if (found) return ImportStatus.OK

            scope.processPropertiesByName(name) {
                found = true
            }
            if (found) return ImportStatus.OK
        }
        return ImportStatus.UNRESOLVED
    }

    @OptIn(SymbolInternals::class)
    private fun FirRegularClassSymbol.getImportStatusOfCallableMembersFromNonSingleton(
        context: CheckerContext,
        name: Name,
    ): ImportStatus {
        var hasStatic = false
        var found = false
        for (scope in listOfNotNull(
            // We first try resolution with declaredMemberScope because it's faster and typically imported members are not from
            // super types.
            context.session.declaredMemberScope(this),

            // Next, we try static scope, which can provide static (Java) members from super classes. Note that it's not available
            // for pure Kotlin classes.
            fir.staticScope(context.sessionHolder),

            // Finally, we fallback to unsubstitutedScope to catch all
            unsubstitutedScope(context)
        )) {
            scope.processFunctionsByName(name) { sym ->
                if (sym.isStatic) hasStatic = true
                found = true
            }
            if (hasStatic) return ImportStatus.OK

            scope.processPropertiesByName(name) { sym ->
                if (sym.isStatic) hasStatic = true
                found = true
            }
            if (hasStatic) return ImportStatus.OK
        }
        return if (found) ImportStatus.CANNOT_BE_IMPORTED
        else ImportStatus.UNRESOLVED
    }

    private fun checkDeprecatedImport(import: FirImport, context: CheckerContext, reporter: DiagnosticReporter) {
        val importedFqName = import.importedFqName ?: return
        if (importedFqName.isRoot || importedFqName.shortName().asString().isEmpty()) return
        val classId = (import as? FirResolvedImport)?.resolvedParentClassId ?: ClassId.topLevel(importedFqName)
        val classLike: FirRegularClassSymbol = classId.resolveToClass(context) ?: return
        context.reportDeprecationIfNeeded(import.source, classLike, null, reporter)
    }
}
