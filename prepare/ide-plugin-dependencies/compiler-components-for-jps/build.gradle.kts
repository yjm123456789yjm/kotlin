plugins {
    kotlin("jvm")
}

@Suppress("UNCHECKED_CAST")
val compilerComponents = (rootProject.extra["kotlinJpsPluginEmbeddedDependencies"] as List<String>) +
    (rootProject.extra["kotlinJpsPluginMavenDependencies"] as List<String>) +
    listOf(":jps:jps-common") -
    listOf(":kotlin-reflect")

publishJarsForIde(compilerComponents)
