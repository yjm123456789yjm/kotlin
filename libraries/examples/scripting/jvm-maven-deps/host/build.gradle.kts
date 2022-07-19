
plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":examples:scripting-jvm-maven-deps"))
    api(project(":kotlin-scripting-jvm-host-unshaded"))
    api(kotlinStdlib())
    // compileOnly(project(":kotlin-reflect"))
    compileOnly(project(":compiler:util"))

    testRuntimeOnly(project(":kotlin-compiler"))
    // testRuntimeOnly(commonDependency("org.jetbrains.kotlin:kotlin-reflect")) { isTransitive = false }
    testRuntimeOnly(project(":kotlin-scripting-compiler"))

    testApi(commonDependency("junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}
