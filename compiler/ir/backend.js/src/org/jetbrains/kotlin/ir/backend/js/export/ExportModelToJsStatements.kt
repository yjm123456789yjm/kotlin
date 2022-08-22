/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.export

import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.*
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd

class ExportModelToJsStatements(
    private val namer: JsStaticContext,
    private val declareNewNamespace: (String) -> String
) {
    private val namespaceToRefMap = mutableMapOf<String, JsNameRef>()

    fun generateModuleExport(module: ExportedModule, internalModuleName: JsName): List<JsStatement> {
        return module.declarations.flatMap { generateDeclarationExport(it, JsNameRef(internalModuleName), esModules = false) }
    }

    fun generateDeclarationExport(
        declaration: ExportedDeclaration,
        namespace: JsExpression?,
        esModules: Boolean
    ): List<JsStatement> {
        return when (declaration) {
            is ExportedNamespace -> {
                require(namespace != null) { "Only namespaced namespaces are allowed" }
                val statements = mutableListOf<JsStatement>()
                val elements = declaration.name.split(".")
                var currentNamespace = ""
                var currentRef: JsExpression = namespace
                for (element in elements) {
                    val newNamespace = "$currentNamespace$$element"
                    val newNameSpaceRef = namespaceToRefMap.getOrPut(newNamespace) {
                        val varName = JsName(declareNewNamespace(newNamespace), false)
                        val namespaceRef = jsElementAccess(element, currentRef)
                        statements += JsVars(
                            JsVars.JsVar(
                                varName,
                                JsAstUtils.or(
                                    namespaceRef,
                                    jsAssignment(
                                        namespaceRef,
                                        JsObjectLiteral()
                                    )
                                )
                            )
                        )
                        JsNameRef(varName)
                    }
                    currentRef = newNameSpaceRef
                    currentNamespace = newNamespace
                }
                statements + declaration.declarations.flatMap { generateDeclarationExport(it, currentRef, esModules) }
            }

            is ExportedFunction -> {
                val name = namer.getNameForStaticDeclaration(declaration.ir)
                if (esModules) {
                    listOf(JsExport(name, alias = JsName(declaration.name, false)))
                } else {
                    if (namespace != null) {
                        listOf(
                            jsAssignment(
                                jsElementAccess(declaration.name, namespace),
                                JsNameRef(name)
                            ).makeStmt()
                        )
                    } else emptyList()
                }
            }

            is ExportedConstructor -> emptyList()
            is ExportedConstructSignature -> emptyList()

            is ExportedProperty -> {
                require(namespace != null) { "Only namespaced properties are allowed" }
                val getter = declaration.irGetter?.let { JsNameRef(namer.getNameForStaticDeclaration(it)) }
                val setter = declaration.irSetter?.let { JsNameRef(namer.getNameForStaticDeclaration(it)) }
                listOf(defineProperty(namespace, declaration.name, getter, setter, namer).makeStmt())
            }

            is ErrorDeclaration -> emptyList()

            is ExportedObject -> {
                require(namespace != null) { "Only namespaced properties are allowed" }
                val newNameSpace = jsElementAccess(declaration.name, namespace)
                val getter = JsNameRef(namer.getNameForStaticDeclaration(declaration.irGetter))
                val staticsExport = declaration.nestedClasses.flatMap { generateDeclarationExport(it, newNameSpace, esModules) }
                listOf(defineProperty(namespace, declaration.name, getter, null, namer).makeStmt()) + staticsExport
            }

            is ExportedRegularClass -> {
                if (declaration.isInterface) return emptyList()
                val newNameSpace = if (namespace != null)
                    jsElementAccess(declaration.name, namespace)
                else
                    prototypeOf(namer.getNameForClass(declaration.ir).makeRef(), namer)
                val name = namer.getNameForStaticDeclaration(declaration.ir)
                val klassExport =
                    if (esModules) {
                        JsExport(name, alias = JsName(declaration.name, false))
                    } else {
                        if (namespace != null) {
                            jsAssignment(
                                newNameSpace,
                                JsNameRef(name)
                            ).makeStmt()
                        } else null
                    }

                // These are only used when exporting secondary constructors annotated with @JsName
                val staticFunctions = declaration.members
                    .filter { it is ExportedFunction && it.isStatic }
                    .takeIf { !declaration.ir.isInner }.orEmpty()

                val enumEntries = declaration.members.filter { it is ExportedProperty && it.isStatic }

                val innerClassesAssignments = declaration.nestedClasses
                    .filter { it.ir.isInner }
                    .map { it.generateInnerClassAssignment(declaration) }

                val staticsExport = (staticFunctions + enumEntries + declaration.nestedClasses)
                    .flatMap { generateDeclarationExport(it, newNameSpace, esModules) }

                listOfNotNull(klassExport) + staticsExport + innerClassesAssignments
            }
        }
    }

    private fun ExportedClass.generateInnerClassAssignment(outerClass: ExportedClass): JsStatement {
        val innerClassRef = namer.getNameForStaticDeclaration(ir).makeRef()
        val outerClassRef = namer.getNameForStaticDeclaration(outerClass.ir).makeRef()
        val companionObject = ir.companionObject()
        val secondaryConstructors = members.filterIsInstanceAnd<ExportedFunction> { it.isStatic }
        val bindConstructor = JsName("__bind_constructor_", false)

        val blockStatements = mutableListOf<JsStatement>(
            JsVars(JsVars.JsVar(bindConstructor, innerClassRef.bindToThis()))
        )

        if (companionObject != null) {
            val companionName = companionObject.getJsNameOrKotlinName().identifier
            blockStatements.add(
                jsAssignment(
                    JsNameRef(companionName, bindConstructor.makeRef()),
                    JsNameRef(companionName, innerClassRef),
                ).makeStmt()
            )
        }

        secondaryConstructors.forEach {
            val currentFunRef = namer.getNameForStaticDeclaration(it.ir).makeRef()
            val assignment = jsAssignment(
                JsNameRef(it.name, bindConstructor.makeRef()),
                currentFunRef.bindToThis()
            ).makeStmt()

            blockStatements.add(assignment)
        }

        blockStatements.add(JsReturn(bindConstructor.makeRef()))

        return defineProperty(
            prototypeOf(outerClassRef, namer),
            name,
            JsFunction(
                emptyScope,
                JsBlock(*blockStatements.toTypedArray()),
                "inner class '$name' getter"
            ),
            null,
            namer
        ).makeStmt()
    }

    private fun JsNameRef.bindToThis(): JsInvocation {
        return JsInvocation(
            JsNameRef("bind", this),
            JsNullLiteral(),
            JsThisRef()
        )
    }
}
