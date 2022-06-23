/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.session

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.checkers.registerCommonCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSwitchableExtensionDeclarationsSymbolProvider
import org.jetbrains.kotlin.fir.java.FirCliSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirDependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.*
import org.jetbrains.kotlin.fir.resolve.scopes.wrapScopeWithJvmMapped
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

@OptIn(PrivateSessionConstructor::class, SessionConfiguration::class)
abstract class FirAbstractSessionFactory<P1 : LibrarySessionParams, P2 : ModuleBasedParams> {
    @Suppress("UNCHECKED_CAST")
    fun createLibrarySession(params: LibrarySessionParams): FirSession {
        return FirCliSession(params.sessionProvider, FirSession.Kind.Library).apply session@{
            val moduleDataProvider = params.dependencyList.moduleDataProvider
            params.dependencyList.moduleDataProvider.allModuleData.forEach {
                params.sessionProvider.registerSession(it, this)
                it.bindSession(this)
            }

            registerCliCompilerOnlyComponents()
            registerCommonComponents(params.languageVersionSettings)
            registerExtraComponentsForLibrary(this, params as P1)

            val kotlinScopeProvider = createKotlinScopeProvider()
            register(FirKotlinScopeProvider::class, kotlinScopeProvider)

            val builtinsModuleData = createModuleDataForBuiltins(
                params.mainModuleName,
                moduleDataProvider.platform,
                moduleDataProvider.analyzerServices
            ).also { it.bindSession(this@session) }

            val providers = mutableListOf(
                FirBuiltinSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
                FirCloneableSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
                FirDependenciesSymbolProviderImpl(this),
            )

            addProvidersToLibrarySession(this, kotlinScopeProvider, providers, params)

            val symbolProvider = FirCompositeSymbolProvider(this, providers)
            register(FirSymbolProvider::class, symbolProvider)
            register(FirProvider::class, FirLibrarySessionProvider(symbolProvider))
        }
    }

    protected open fun registerExtraComponentsForLibrary(session: FirSession, params: P1) {}

    protected open fun addProvidersToLibrarySession(
        session: FirCliSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        providers: MutableList<FirSymbolProvider>,
        params: P1
    ) {
    }

    private fun createModuleDataForBuiltins(
        parentModuleName: Name,
        platform: TargetPlatform,
        analyzerServices: PlatformDependentAnalyzerServices
    ): FirModuleData {
        return DependencyListForCliModule.createDependencyModuleData(
            Name.special("<builtins of ${parentModuleName.identifier}"),
            platform,
            analyzerServices,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun createModuleBasedSession(params: ModuleBasedParams): FirSession {
        return FirCliSession(params.sessionProvider, FirSession.Kind.Source).apply session@{
            params.moduleData.bindSession(this@session)
            params.sessionProvider.registerSession(params.moduleData, this@session)
            registerModuleData(params.moduleData)
            registerCliCompilerOnlyComponents()
            registerCommonComponents(params.languageVersionSettings)
            registerResolveComponents(params.lookupTracker, params.enumWhenTracker)
            registerExtraComponentsForModuleBased(this, params as P2)

            val kotlinScopeProvider = createKotlinScopeProvider()
            register(FirKotlinScopeProvider::class, kotlinScopeProvider)

            val firProvider = FirProviderImpl(this, kotlinScopeProvider)
            register(FirProvider::class, firProvider)

            FirSessionConfigurator(this).apply {
                registerCommonCheckers()
                registerExtraCheckers(this@apply)

                for (extensionRegistrar in params.extensionRegistrars) {
                    registerExtensions(extensionRegistrar.configure())
                }
                params.init(this@apply)
            }.configure()

            val dependenciesSymbolProvider = FirDependenciesSymbolProviderImpl(this)
            val generatedSymbolsProvider = FirSwitchableExtensionDeclarationsSymbolProvider.create(this)

            val providers = createProvidersForModuleBased(
                this, kotlinScopeProvider, firProvider.symbolProvider, generatedSymbolsProvider, dependenciesSymbolProvider,
                params
            )

            register(FirSymbolProvider::class, FirCompositeSymbolProvider(this, providers))

            generatedSymbolsProvider?.let { register(FirSwitchableExtensionDeclarationsSymbolProvider::class, it) }

            register(
                FirDependenciesSymbolProvider::class,
                dependenciesSymbolProvider
            )

            complete(this, params)
        }
    }

    protected open fun registerExtraComponentsForModuleBased(session: FirSession, params: P2) {
    }

    protected abstract fun createKotlinScopeProvider(): FirKotlinScopeProvider

    protected open fun registerExtraCheckers(configurator: FirSessionConfigurator) {
    }

    protected abstract fun createProvidersForModuleBased(
        session: FirSession, kotlinScopeProvider: FirKotlinScopeProvider, symbolProvider: FirSymbolProvider,
        generatedSymbolsProvider: FirSwitchableExtensionDeclarationsSymbolProvider?,
        dependenciesSymbolProvider: FirDependenciesSymbolProviderImpl,
        params: P2
    ): List<FirSymbolProvider>

    protected open fun complete(session: FirSession, params: P2){
    }
}

open class LibrarySessionParams(
    val mainModuleName: Name,
    val sessionProvider: FirProjectSessionProvider,
    val dependencyList: DependencyListForCliModule,
    val languageVersionSettings: LanguageVersionSettings
)

open class ModuleBasedParams(
    val moduleData: FirModuleData,
    val sessionProvider: FirProjectSessionProvider,
    val extensionRegistrars: List<FirExtensionRegistrar>,
    val languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    val lookupTracker: LookupTracker? = null,
    val enumWhenTracker: EnumWhenTracker? = null,
    val init: FirSessionConfigurator.() -> Unit = {}
)
