
plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":examples:scripting-jvm-simple-script"))
    api(project(":kotlin-scripting-jvm-host-unshaded"))
    api(project(":kotlin-script-util"))
    testRuntimeOnly(project(":kotlin-compiler"))
    testRuntimeOnly(project(":kotlin-scripting-compiler"))
    // ./gradlew :examples:scripting-jvm-simple-script-host:test works
    // testRuntimeOnly(commonDependency("org.jetbrains.kotlin:kotlin-reflect"))
    testApi(commonDependency("junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}
