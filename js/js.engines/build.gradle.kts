
plugins {
    kotlin("jvm")
    id("jps-compatible")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":compiler:util"))
    api(project(":js:js.ast"))
    api(project(":js:js.translator"))
    api(intellijCore())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("io.ktor:ktor-client-core:2.0.1")
    implementation("io.ktor:ktor-client-cio:2.0.1")
    implementation("io.ktor:ktor-client-websockets:2.0.1")
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
