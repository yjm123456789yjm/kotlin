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
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.calls.ConeCallConflictResolverFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirSyntheticNamesProvider
import org.jetbrains.kotlin.fir.scopes.FirPlatformClassMapper
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.fir.session.FirSessionFactory.createLibrarySession
import org.jetbrains.kotlin.fir.session.FirSessionFactory.createModuleBasedSessionSession
import org.jetbrains.kotlin.fir.session.KlibBasedSymbolProvider
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

object FirJsSessionFactory {
    fun createJsLibrarySession(
        mainModuleName: Name,
        sessionProvider: FirProjectSessionProvider,
        dependencyListForCliModule: DependencyListForCliModule,
        module: TestModule,
        testServices: TestServices,
        configuration: CompilerConfiguration,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    ): FirSession {
        val moduleDataProvider = dependencyListForCliModule.moduleDataProvider
        return createLibrarySession(
            mainModuleName,
            sessionProvider,
            moduleDataProvider,
            languageVersionSettings,
            null,
        ) { session, kotlinScopeProvider, providers ->
            providers.addAll(resolveJsLibraries(module, testServices, configuration).map {
                KlibBasedSymbolProvider(session, moduleDataProvider, kotlinScopeProvider, it)
            })
        }
    }

    fun createJsModuleBasedSession(
        moduleData: FirModuleData,
        sessionProvider: FirProjectSessionProvider,
        extensionRegistrars: List<FirExtensionRegistrar>,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
        lookupTracker: LookupTracker? = null,
        init: FirSessionFactory.FirSessionConfigurator.() -> Unit = {}
    ): FirSession {
        return createModuleBasedSessionSession(
            moduleData,
            sessionProvider,
            extensionRegistrars,
            languageVersionSettings,
            lookupTracker,
            null,
            init,
            registerExtraComponents = { it.registerJsSpecificResolveComponents() },
            registerExtraCheckers = { it.registerJsCheckers() },
            createProviders = { _, _, symbolProvider, generatedSymbolsProvider, dependenciesSymbolProvider ->
                listOfNotNull(
                    symbolProvider,
                    generatedSymbolsProvider,
                    dependenciesSymbolProvider,
                )
            }
        )
    }

    @OptIn(SessionConfiguration::class)
    fun FirSession.registerJsSpecificResolveComponents() {
        register(FirVisibilityChecker::class, FirVisibilityChecker.Default)
        register(ConeCallConflictResolverFactory::class, JsCallConflictResolverFactory)
        register(FirPlatformClassMapper::class, FirPlatformClassMapper.Default)
        register(FirSyntheticNamesProvider::class, FirJsSyntheticNamesProvider)
        register(FirOverridesBackwardCompatibilityHelper::class, FirOverridesBackwardCompatibilityHelper.Default())
    }
}