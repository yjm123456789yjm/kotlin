
plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":examples:scripting-jvm-simple-script"))
    compileOnly(project(":kotlin-scripting-jvm-host-unshaded"))
    testRuntimeOnly(project(":kotlin-compiler-embeddable"))
    testRuntimeOnly(project(":kotlin-scripting-compiler-embeddable"))
    testRuntimeOnly(project(":kotlin-scripting-jvm-host"))
    // ./gradlew :examples:scripting-jvm-embeddable-host:test works
    // testRuntimeOnly(commonDependency("org.jetbrains.kotlin:kotlin-reflect"))
    testRuntimeOnly(commonDependency("com.google.guava:guava"))
    testApi(commonDependency("junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

