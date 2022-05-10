plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    api(kotlinStdlib())
    api("org.jetbrains:annotations:13.0")
    api(project(":kotlin-compiler"))
    api(project(":kotlin-daemon"))
    api(project(":kotlin-main-kts"))
    api(project(":kotlin-reflect"))
    api(project(":kotlin-script-runtime"))
    api(commonDependency("org.jetbrains.intellij.deps:trove4j"))

    // All *-maven-* compiler plugins are built by maven (not gradle) see libraries/tools/*-maven-*/pom.xml
    // So it's not possible to refer to gradle subprojects for compiler plugins
    // Surprisingly, referring to not existing maven artifacts works
    api("org.jetbrains.kotlin:kotlin-maven-serialization:${project.version}")
    api("org.jetbrains.kotlin:kotlin-maven-sam-with-receiver:${project.version}")
    api("org.jetbrains.kotlin:kotlin-maven-allopen:${project.version}")
    api("org.jetbrains.kotlin:kotlin-maven-lombok:${project.version}")
    api("org.jetbrains.kotlin:kotlin-maven-noarg:${project.version}")

//    api(project(":kotlin-scripting-common"))
//    api(project(":kotlin-annotation-processing-runtime"))
//    api(project(":kotlin-stdlib-js"))
//    api(project(":kotlin-stdlib-jdk7"))
//    api(project(":kotlin-stdlib-jdk8"))
}

publish()

noDefaultJar() // Don't produce any jar. It's pom-only maven artifact
