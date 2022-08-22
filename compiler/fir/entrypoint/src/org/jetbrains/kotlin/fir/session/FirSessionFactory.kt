/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.session

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.checkers.registerJvmCheckers
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.deserialization.ModuleDataProvider
import org.jetbrains.kotlin.fir.deserialization.SingleModuleDataProvider
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.java.FirCliSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.java.JavaSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.JvmClassFileBasedSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.OptionalAnnotationClassesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.*
import org.jetbrains.kotlin.fir.resolve.scopes.wrapScopeWithJvmMapped
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectEnvironment
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.jsResolveLibraries
import org.jetbrains.kotlin.ir.backend.js.toResolverLogger
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

@OptIn(PrivateSessionConstructor::class)
object FirSessionFactory : FirAbstractSessionFactory() {
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
            moduleName,
            sessionProvider,
            dependencyList,
            projectEnvironment,
            librariesScope,
            packagePartProvider,
            languageVersionSettings
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
            mainModuleData,
            sessionProvider,
            javaSourcesScope,
            projectEnvironment,
            incrementalCompilationContext,
            extensionRegistrars,
            languageVersionSettings,
            lookupTracker,
            enumWhenTracker,
            needRegisterJavaElementFinder,
            sessionConfigurator
        )
    }

    fun createLibrarySession(
        mainModuleName: Name,
        sessionProvider: FirProjectSessionProvider,
        dependencyList: DependencyListForCliModule,
        projectEnvironment: AbstractProjectEnvironment,
        scope: AbstractProjectFileSearchScope,
        packagePartProvider: PackagePartProvider,
        languageVersionSettings: LanguageVersionSettings,
    ): FirSession {
        return createLibrarySession(
            mainModuleName,
            sessionProvider,
            dependencyList.moduleDataProvider,
            languageVersionSettings,
            registerExtraComponents = { it.registerCommonJavaComponents(projectEnvironment.getJavaModuleResolver()) },
            createKotlinScopeProvider = { FirKotlinScopeProvider(::wrapScopeWithJvmMapped) },
            createProviders = { session, builtinsModuleData, kotlinScopeProvider ->
                listOf(
                    JvmClassFileBasedSymbolProvider(
                        session,
                        dependencyList.moduleDataProvider,
                        kotlinScopeProvider,
                        packagePartProvider,
                        projectEnvironment.getKotlinClassFinder(scope),
                        projectEnvironment.getFirJavaFacade(session, dependencyList.moduleDataProvider.allModuleData.last(), scope)
                    ),
                    FirBuiltinSymbolProvider(session, builtinsModuleData, kotlinScopeProvider),
                    FirCloneableSymbolProvider(session, builtinsModuleData, kotlinScopeProvider),
                    FirDependenciesSymbolProviderImpl(session),
                    OptionalAnnotationClassesProvider(session, dependencyList.moduleDataProvider, kotlinScopeProvider, packagePartProvider)
                )
            }
        )
    }

    @OptIn(SessionConfiguration::class)
    fun createModuleBasedSession(
        moduleData: FirModuleData,
        sessionProvider: FirProjectSessionProvider,
        javaSourcesScope: AbstractProjectFileSearchScope,
        projectEnvironment: AbstractProjectEnvironment,
        incrementalCompilationContext: IncrementalCompilationContext?,
        extensionRegistrars: List<FirExtensionRegistrar>,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
        lookupTracker: LookupTracker? = null,
        enumWhenTracker: EnumWhenTracker? = null,
        needRegisterJavaElementFinder: Boolean,
        init: FirSessionConfigurator.() -> Unit = {}
    ): FirSession {
        return createModuleBasedSession(
            moduleData,
            sessionProvider,
            extensionRegistrars,
            languageVersionSettings,
            lookupTracker,
            enumWhenTracker,
            init,
            registerExtraComponents = {
                it.registerCommonJavaComponents(projectEnvironment.getJavaModuleResolver())
                it.registerJavaSpecificResolveComponents()
            },
            registerExtraCheckers = { it.registerJvmCheckers() },
            createKotlinScopeProvider = { FirKotlinScopeProvider(::wrapScopeWithJvmMapped) },
            createProviders = { session, kotlinScopeProvider, symbolProvider, generatedSymbolsProvider, dependenciesSymbolProvider ->
                var symbolProviderForBinariesFromIncrementalCompilation: JvmClassFileBasedSymbolProvider? = null
                var optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation: OptionalAnnotationClassesProvider? = null
                incrementalCompilationContext?.let {
                    if (it.precompiledBinariesPackagePartProvider != null && it.precompiledBinariesFileScope != null) {
                        val moduleDataProvider = SingleModuleDataProvider(moduleData)
                        symbolProviderForBinariesFromIncrementalCompilation =
                            JvmClassFileBasedSymbolProvider(
                                session,
                                moduleDataProvider,
                                kotlinScopeProvider,
                                it.precompiledBinariesPackagePartProvider,
                                projectEnvironment.getKotlinClassFinder(it.precompiledBinariesFileScope),
                                projectEnvironment.getFirJavaFacade(session, moduleData, it.precompiledBinariesFileScope),
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

                val javaSymbolProvider = JavaSymbolProvider(session, projectEnvironment.getFirJavaFacade(session, moduleData, javaSourcesScope))
                session.register(JavaSymbolProvider::class, javaSymbolProvider)

                listOfNotNull(
                    symbolProvider,
                    *(incrementalCompilationContext?.previousFirSessionsSymbolProviders?.toTypedArray() ?: emptyArray()),
                    symbolProviderForBinariesFromIncrementalCompilation,
                    generatedSymbolsProvider,
                    javaSymbolProvider,
                    dependenciesSymbolProvider,
                    optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation,
                )
            }
        ).also {
            if (needRegisterJavaElementFinder) {
                projectEnvironment.registerAsJavaElementFinder(it)
            }
        }
    }

    @OptIn(SessionConfiguration::class)
    fun createJvmLibrarySession(
        mainModuleName: Name,
        sessionProvider: FirProjectSessionProvider,
        moduleDataProvider: ModuleDataProvider,
        scope: AbstractProjectFileSearchScope,
        projectEnvironment: AbstractProjectEnvironment,
        packagePartProvider: PackagePartProvider,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    ): FirSession =
        createLibrarySession(
            sessionProvider, moduleDataProvider, languageVersionSettings
        ) {
            registerCommonJavaComponents(projectEnvironment.getJavaModuleResolver())

            val kotlinScopeProvider = FirKotlinScopeProvider(::wrapScopeWithJvmMapped)
            register(FirKotlinScopeProvider::class, kotlinScopeProvider)

            val classFileBasedSymbolProvider = JvmClassFileBasedSymbolProvider(
                this,
                moduleDataProvider,
                kotlinScopeProvider,
                packagePartProvider,
                projectEnvironment.getKotlinClassFinder(scope),
                projectEnvironment.getFirJavaFacade(this, moduleDataProvider.allModuleData.last(), scope)
            )

            val optionalAnnotationClassesProvider = OptionalAnnotationClassesProvider(
                this,
                moduleDataProvider,
                kotlinScopeProvider,
                packagePartProvider
            )

            val builtinsModuleData = createModuleDataForBuiltins(
                mainModuleName,
                moduleDataProvider.platform,
                moduleDataProvider.analyzerServices
            ).also { it.bindSession(this) }

            val symbolProvider = FirCompositeSymbolProvider(
                this,
                listOf(
                    classFileBasedSymbolProvider,
                    FirBuiltinSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
                    FirCloneableSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
                    FirDependenciesSymbolProviderImpl(this),
                    optionalAnnotationClassesProvider
                )
            )
            register(FirSymbolProvider::class, symbolProvider)
            register(FirProvider::class, FirLibrarySessionProvider(symbolProvider))
        }

    @OptIn(SessionConfiguration::class)
    fun createJsLibrarySession(
        mainModuleName: Name,
        libraries: List<String>,
        configuration: CompilerConfiguration,
        sessionProvider: FirProjectSessionProvider,
        moduleDataProvider: ModuleDataProvider,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
    ) = createLibrarySession(
        sessionProvider, moduleDataProvider, languageVersionSettings
    ) {
        val kotlinScopeProvider = FirKotlinScopeProvider()
        register(FirKotlinScopeProvider::class, kotlinScopeProvider)

        val builtinsModuleData = createModuleDataForBuiltins(
            mainModuleName,
            moduleDataProvider.platform,
            moduleDataProvider.analyzerServices
        ).also { it.bindSession(this) }

        val repositories = configuration[JSConfigurationKeys.REPOSITORIES] ?: emptyList()
        val logger = configuration[IrMessageLogger.IR_MESSAGE_LOGGER].toResolverLogger()
        val klibProviders = jsResolveLibraries(libraries, repositories, logger).getFullResolvedList().map {
            KlibBasedSymbolProvider(this, moduleDataProvider, kotlinScopeProvider, it)
        }

        val otherProviders = listOf(
            FirCloneableSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
            FirDependenciesSymbolProviderImpl(this),
            FirBuiltinSymbolProvider(this, builtinsModuleData, kotlinScopeProvider),
        )

        val symbolProvider = FirCompositeSymbolProvider(this, klibProviders + otherProviders)

        register(FirSymbolProvider::class, symbolProvider)
        register(FirProvider::class, FirLibrarySessionProvider(symbolProvider))
    }

    fun createLibrarySession(
        sessionProvider: FirProjectSessionProvider,
        moduleDataProvider: ModuleDataProvider,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
        configure: FirCliSession.() -> Unit
    ): FirSession {
        return FirCliSession(sessionProvider, FirSession.Kind.Library).apply session@{
            moduleDataProvider.allModuleData.forEach {
                sessionProvider.registerSession(it, this)
                it.bindSession(this)
            }
            registerCliCompilerOnlyComponents()
            registerCommonComponents(languageVersionSettings)
            configure()
        }
    }

    @OptIn(SessionConfiguration::class)
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
            // Empty stub for tests
            register(FirLanguageSettingsComponent::class, FirLanguageSettingsComponent(
                object : LanguageVersionSettings {

                    private fun stub(): Nothing = TODO(
                        "It does not yet have well-defined semantics for tests." +
                                "If you're seeing this, implement it in a test-specific way"
                    )

                    override fun getFeatureSupport(feature: LanguageFeature): LanguageFeature.State {
                        return LanguageFeature.State.DISABLED
                    }

                    override fun isPreRelease(): Boolean = stub()

                    override fun <T> getFlag(flag: AnalysisFlag<T>): T = stub()

                    override val apiVersion: ApiVersion
                        get() = stub()
                    override val languageVersion: LanguageVersion
                        get() = stub()
                }
            ))
        }
    }

    fun createModuleDataForBuiltins(
        parentModuleName: Name,
        platform: TargetPlatform,
        analyzerServices: PlatformDependentAnalyzerServices
    ): FirModuleData {
        return DependencyListForCliModule.createDependencyModuleData(
            Name.special("<builtins of ${parentModuleName.asString()}"),
            platform,
            analyzerServices,
        )
    }
}
