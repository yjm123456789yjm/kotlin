plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "org.jetbrains.it"
version = "1.0"

publishing {
	repositories {
		maven("$rootDir/repo")
	}
}

kotlin {
    listOf(linuxX64(), linuxArm64()).forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                create("dummy") {
                    defFile(project.file("$rootDir/nativeLib/dummy/dummy.def"))
                    includeDirs.allHeaders("$rootDir/nativeLib/dummy")
                }
            }
        }
    }
}
