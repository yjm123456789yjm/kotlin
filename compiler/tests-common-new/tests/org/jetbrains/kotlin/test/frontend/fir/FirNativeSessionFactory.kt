/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.FirOverridesBackwardCompatibilityHelper
import org.jetbrains.kotlin.fir.analysis.jvm.FirJvmOverridesBackwardCompatibilityHelper
import org.jetbrains.kotlin.fir.checkers.registerNativeCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSwitchableExtensionDeclarationsSymbolProvider
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.FirJavaSyntheticNamesProvider
import org.jetbrains.kotlin.fir.resolve.calls.ConeCallConflictResolverFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirSyntheticNamesProvider
import org.jetbrains.kotlin.fir.resolve.calls.jvm.JvmCallConflictResolverFactory
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirDependenciesSymbolProviderImpl
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.FirPlatformClassMapper
import org.jetbrains.kotlin.fir.session.*
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.Name

object FirNativeSessionFactory : FirAbstractSessionFactory<NativeLibrarySessionParams, NativeModuleBasedParams>() {
    override fun createKotlinScopeProvider(): FirKotlinScopeProvider {
        return FirKotlinScopeProvider { _, declaredMemberScope, _, _ -> declaredMemberScope }
    }

    override fun registerExtraCheckers(configurator: FirSessionConfigurator) {
        configurator.registerNativeCheckers()
    }

    override fun createProvidersForModuleBased(
        session: FirSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        symbolProvider: FirSymbolProvider,
        generatedSymbolsProvider: FirSwitchableExtensionDeclarationsSymbolProvider?,
        dependenciesSymbolProvider: FirDependenciesSymbolProviderImpl,
        params: NativeModuleBasedParams
    ): List<FirSymbolProvider> {
        return listOfNotNull(
            symbolProvider,
            generatedSymbolsProvider,
            dependenciesSymbolProvider,
        )
    }

    @OptIn(SessionConfiguration::class)
    override fun registerExtraComponentsForModuleBased(session: FirSession, params: NativeModuleBasedParams) {
        session.register(FirVisibilityChecker::class, FirVisibilityChecker.Default)
        session.register(ConeCallConflictResolverFactory::class, NativeCallConflictResolverFactory)
        session.register(FirPlatformClassMapper::class, FirPlatformClassMapper.Default)
        session.register(FirSyntheticNamesProvider::class, FirNativeSyntheticNamesProvider)
        session.register(FirOverridesBackwardCompatibilityHelper::class, FirOverridesBackwardCompatibilityHelper.Default())
    }
}

open class NativeLibrarySessionParams(
    mainModuleName: Name,
    sessionProvider: FirProjectSessionProvider,
    dependencyList: DependencyListForCliModule,
    languageVersionSettings: LanguageVersionSettings
) : LibrarySessionParams(mainModuleName, sessionProvider, dependencyList, languageVersionSettings)

open class NativeModuleBasedParams(
    moduleData: FirModuleData,
    sessionProvider: FirProjectSessionProvider,
    extensionRegistrars: List<FirExtensionRegistrar>,
    languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    lookupTracker: LookupTracker? = null,
    enumWhenTracker: EnumWhenTracker? = null,
    init: FirSessionConfigurator.() -> Unit = {}
) : ModuleBasedParams(moduleData, sessionProvider, extensionRegistrars, languageVersionSettings, lookupTracker, enumWhenTracker, init)
