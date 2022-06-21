/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.FirOverridesBackwardCompatibilityHelper
import org.jetbrains.kotlin.fir.checkers.registerJsCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSwitchableExtensionDeclarationsSymbolProvider
import org.jetbrains.kotlin.fir.java.FirCliSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.calls.ConeCallConflictResolverFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirSyntheticNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirDependenciesSymbolProviderImpl
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.FirPlatformClassMapper
import org.jetbrains.kotlin.fir.session.*
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

object FirJsSessionFactory : FirAbstractSessionFactory<JsLibrarySessionParams, JsModuleBasedParams>() {
    override fun createKotlinScopeProvider(): FirKotlinScopeProvider {
        return FirKotlinScopeProvider { _, declaredMemberScope, _, _ -> declaredMemberScope }
    }

    override fun addProvidersToLibrarySession(
        session: FirCliSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        providers: MutableList<FirSymbolProvider>,
        params: JsLibrarySessionParams
    ) {
        providers.addAll(resolveJsLibraries(params.module, params.testServices, params.configuration).map {
            KlibBasedSymbolProvider(session, params.dependencyList.moduleDataProvider, kotlinScopeProvider, it)
        })
    }

    @OptIn(SessionConfiguration::class)
    override fun registerExtraComponentsForModuleBased(session: FirSession, params: JsModuleBasedParams) {
        session.register(FirVisibilityChecker::class, FirVisibilityChecker.Default)
        session.register(ConeCallConflictResolverFactory::class, JsCallConflictResolverFactory)
        session.register(FirPlatformClassMapper::class, FirPlatformClassMapper.Default)
        session.register(FirSyntheticNamesProvider::class, FirJsSyntheticNamesProvider)
        session.register(FirOverridesBackwardCompatibilityHelper::class, FirOverridesBackwardCompatibilityHelper.Default())
    }

    override fun registerExtraCheckers(configurator: FirSessionFactory.FirSessionConfigurator) {
        configurator.registerJsCheckers()
    }

    override fun createProvidersForModuleBased(
        session: FirSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        symbolProvider: FirSymbolProvider,
        generatedSymbolsProvider: FirSwitchableExtensionDeclarationsSymbolProvider?,
        dependenciesSymbolProvider: FirDependenciesSymbolProviderImpl,
        params: JsModuleBasedParams
    ): List<FirSymbolProvider> {
        return listOfNotNull(
            symbolProvider,
            generatedSymbolsProvider,
            dependenciesSymbolProvider,
        )
    }
}

open class JsLibrarySessionParams(
    mainModuleName: Name,
    sessionProvider: FirProjectSessionProvider,
    dependencyList: DependencyListForCliModule,
    languageVersionSettings: LanguageVersionSettings,
    val module: TestModule,
    val testServices: TestServices,
    val configuration: CompilerConfiguration
) : LibrarySessionParams(mainModuleName, sessionProvider, dependencyList, languageVersionSettings)

class JsModuleBasedParams(
    moduleData: FirModuleData,
    sessionProvider: FirProjectSessionProvider,
    extensionRegistrars: List<FirExtensionRegistrar>,
    languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    lookupTracker: LookupTracker? = null,
    enumWhenTracker: EnumWhenTracker? = null,
    init: FirSessionFactory.FirSessionConfigurator.() -> Unit = {}
) : ModuleBasedParams(
    moduleData, sessionProvider, extensionRegistrars, languageVersionSettings, lookupTracker, enumWhenTracker, init
)