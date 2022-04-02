plugins {
    id("org.jetbrains.kotlin.multiplatform").version("<pluginMarkerVersion>")
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    <SingleNativeTarget>("host") {
        compilations.all {
            kotlinOptions.verbose.set(true)
            enableEndorsedLibs = true
        }
    }
}
