/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.session

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckers
import org.jetbrains.kotlin.fir.analysis.checkersComponent
import org.jetbrains.kotlin.fir.analysis.extensions.additionalCheckers
import org.jetbrains.kotlin.fir.checkers.registerJvmCheckers
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.deserialization.SingleModuleDataProvider
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.java.FirCliSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.java.JavaSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.JvmClassFileBasedSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.OptionalAnnotationClassesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.*
import org.jetbrains.kotlin.fir.resolve.scopes.wrapScopeWithJvmMapped
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectEnvironment
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

@OptIn(PrivateSessionConstructor::class, SessionConfiguration::class)
object FirSessionFactory : FirAbstractSessionFactory<CommonOrJvmLibraryParams, CommonOrJvmModuleBasedParams>() {
    class FirSessionConfigurator(private val session: FirSession) {
        private val registeredExtensions: MutableList<BunchOfRegisteredExtensions> = mutableListOf(BunchOfRegisteredExtensions.empty())

        fun registerExtensions(extensions: BunchOfRegisteredExtensions) {
            registeredExtensions += extensions
        }

        fun useCheckers(checkers: ExpressionCheckers) {
            session.checkersComponent.register(checkers)
        }

        fun useCheckers(checkers: DeclarationCheckers) {
            session.checkersComponent.register(checkers)
        }

        fun useCheckers(checkers: TypeCheckers) {
            session.checkersComponent.register(checkers)
        }

        @SessionConfiguration
        fun configure() {
            session.extensionService.registerExtensions(registeredExtensions.reduce(BunchOfRegisteredExtensions::plus))
            session.extensionService.additionalCheckers.forEach(session.checkersComponent::register)
        }
    }

    data class IncrementalCompilationContext(
        // assuming that providers here do not intersect with the one being built from precompiled binaries
        // (maybe easiest way to achieve is to delete libraries
        // TODO: consider passing something more abstract instead of precompiler component, in order to avoid file ops here
        val previousFirSessionsSymbolProviders: Collection<FirSymbolProvider>,
        val precompiledBinariesPackagePartProvider: PackagePartProvider?,
        val precompiledBinariesFileScope: AbstractProjectFileSearchScope?
    )

    override fun registerExtraComponentsForLibrary(session: FirSession, params: CommonOrJvmLibraryParams) {
        session.registerCommonJavaComponents(params.projectEnvironment.getJavaModuleResolver())
    }

    override fun createKotlinScopeProvider(): FirKotlinScopeProvider {
        return FirKotlinScopeProvider(::wrapScopeWithJvmMapped)
    }

    override fun addProvidersToLibrarySession(
        session: FirCliSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        providers: MutableList<FirSymbolProvider>,
        params: CommonOrJvmLibraryParams
    ) {
        providers.add(
            0, JvmClassFileBasedSymbolProvider(
                session,
                params.dependencyList.moduleDataProvider,
                kotlinScopeProvider,
                params.packagePartProvider,
                params.projectEnvironment.getKotlinClassFinder(params.scope),
                params.projectEnvironment.getFirJavaFacade(session, params.dependencyList.moduleDataProvider.allModuleData.last(), params.scope)
            )
        )
        providers.add(
            OptionalAnnotationClassesProvider(session, params.dependencyList.moduleDataProvider, kotlinScopeProvider, params.packagePartProvider)
        )
    }

    inline fun createSessionWithDependencies(
        moduleName: Name,
        platform: TargetPlatform,
        analyzerServices: PlatformDependentAnalyzerServices,
        externalSessionProvider: FirProjectSessionProvider?,
        projectEnvironment: AbstractProjectEnvironment,
        languageVersionSettings: LanguageVersionSettings,
        javaSourcesScope: AbstractProjectFileSearchScope,
        librariesScope: AbstractProjectFileSearchScope,
        lookupTracker: LookupTracker?,
        enumWhenTracker: EnumWhenTracker?,
        incrementalCompilationContext: IncrementalCompilationContext?,
        extensionRegistrars: List<FirExtensionRegistrar>,
        needRegisterJavaElementFinder: Boolean,
        dependenciesConfigurator: DependencyListForCliModule.Builder.() -> Unit = {},
        noinline sessionConfigurator: FirSessionConfigurator.() -> Unit = {},
    ): FirSession {
        val dependencyList = DependencyListForCliModule.build(moduleName, platform, analyzerServices, dependenciesConfigurator)
        val sessionProvider = externalSessionProvider ?: FirProjectSessionProvider()
        val packagePartProvider = projectEnvironment.getPackagePartProvider(librariesScope)

        createLibrarySession(
            CommonOrJvmLibraryParams(
                moduleName,
                sessionProvider,
                dependencyList,
                languageVersionSettings,
                projectEnvironment,
                librariesScope,
                packagePartProvider
            )
        )

        val mainModuleData = FirModuleDataImpl(
            moduleName,
            dependencyList.regularDependencies,
            dependencyList.dependsOnDependencies,
            dependencyList.friendsDependencies,
            dependencyList.platform,
            dependencyList.analyzerServices
        )

        return createModuleBasedSession(
            CommonOrJvmModuleBasedParams(
                mainModuleData,
                sessionProvider,
                extensionRegistrars,
                javaSourcesScope,
                projectEnvironment,
                languageVersionSettings = languageVersionSettings,
                lookupTracker = lookupTracker,
                enumWhenTracker = enumWhenTracker,
                needRegisterJavaElementFinder,
                incrementalCompilationContext,
                init = sessionConfigurator,
            ),
        )
    }

    override fun registerExtraComponentsForModuleBased(session: FirSession, params: CommonOrJvmModuleBasedParams) {
        session.registerCommonJavaComponents(params.projectEnvironment.getJavaModuleResolver())
        session.registerJavaSpecificResolveComponents()
    }

    override fun registerExtraCheckers(configurator: FirSessionConfigurator) {
        configurator.registerJvmCheckers()
    }

    override fun createProvidersForModuleBased(
        session: FirSession,
        kotlinScopeProvider: FirKotlinScopeProvider,
        symbolProvider: FirSymbolProvider,
        generatedSymbolsProvider: FirSwitchableExtensionDeclarationsSymbolProvider?,
        dependenciesSymbolProvider: FirDependenciesSymbolProviderImpl,
        params: CommonOrJvmModuleBasedParams
    ): List<FirSymbolProvider> {
        var symbolProviderForBinariesFromIncrementalCompilation: JvmClassFileBasedSymbolProvider? = null
        var optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation: OptionalAnnotationClassesProvider? = null
        params.incrementalCompilationContext?.let {
            if (it.precompiledBinariesPackagePartProvider != null && it.precompiledBinariesFileScope != null) {
                val moduleDataProvider = SingleModuleDataProvider(params.moduleData)
                symbolProviderForBinariesFromIncrementalCompilation =
                    JvmClassFileBasedSymbolProvider(
                        session,
                        moduleDataProvider,
                        kotlinScopeProvider,
                        it.precompiledBinariesPackagePartProvider,
                        params.projectEnvironment.getKotlinClassFinder(it.precompiledBinariesFileScope),
                        params.projectEnvironment.getFirJavaFacade(session, params.moduleData, it.precompiledBinariesFileScope),
                        defaultDeserializationOrigin = FirDeclarationOrigin.Precompiled
                    )
                optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation =
                    OptionalAnnotationClassesProvider(
                        session,
                        moduleDataProvider,
                        kotlinScopeProvider,
                        it.precompiledBinariesPackagePartProvider,
                        defaultDeserializationOrigin = FirDeclarationOrigin.Precompiled
                    )
            }
        }

        return listOfNotNull(
            symbolProvider,
            *(params.incrementalCompilationContext?.previousFirSessionsSymbolProviders?.toTypedArray() ?: emptyArray()),
            symbolProviderForBinariesFromIncrementalCompilation,
            generatedSymbolsProvider,
            JavaSymbolProvider(session, params.projectEnvironment.getFirJavaFacade(session, params.moduleData, params.javaSourcesScope)),
            dependenciesSymbolProvider,
            optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation,
        )
    }

    override fun complete(session: FirSession, params: CommonOrJvmModuleBasedParams) {
        if (params.needRegisterJavaElementFinder) {
            params.projectEnvironment.registerAsJavaElementFinder(session)
        }
    }

    @TestOnly
    fun createEmptySession(): FirSession {
        return object : FirSession(null, Kind.Source) {}.apply {
            val moduleData = FirModuleDataImpl(
                Name.identifier("<stub module>"),
                dependencies = emptyList(),
                dependsOnDependencies = emptyList(),
                friendDependencies = emptyList(),
                platform = JvmPlatforms.unspecifiedJvmPlatform,
                analyzerServices = JvmPlatformAnalyzerServices
            )
            registerModuleData(moduleData)
            moduleData.bindSession(this)
        }
    }
}

open class CommonOrJvmLibraryParams(
    mainModuleName: Name,
    sessionProvider: FirProjectSessionProvider,
    dependencyList: DependencyListForCliModule,
    languageVersionSettings: LanguageVersionSettings,
    val projectEnvironment: AbstractProjectEnvironment,
    val scope: AbstractProjectFileSearchScope,
    val packagePartProvider: PackagePartProvider,
) : LibrarySessionParams(mainModuleName, sessionProvider, dependencyList, languageVersionSettings)

open class CommonOrJvmModuleBasedParams(
    moduleData: FirModuleData,
    sessionProvider: FirProjectSessionProvider,
    extensionRegistrars: List<FirExtensionRegistrar>,
    val javaSourcesScope: AbstractProjectFileSearchScope,
    val projectEnvironment: AbstractProjectEnvironment,
    languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    lookupTracker: LookupTracker? = null,
    enumWhenTracker: EnumWhenTracker? = null,
    val needRegisterJavaElementFinder: Boolean = true,
    val incrementalCompilationContext: FirSessionFactory.IncrementalCompilationContext? = null,
    init: FirSessionFactory.FirSessionConfigurator.() -> Unit = {}
) : ModuleBasedParams(moduleData, sessionProvider, extensionRegistrars, languageVersionSettings, lookupTracker, enumWhenTracker, init)

