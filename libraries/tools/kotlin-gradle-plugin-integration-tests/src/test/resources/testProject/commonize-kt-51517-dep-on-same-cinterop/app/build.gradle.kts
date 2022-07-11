plugins {
    kotlin("multiplatform")
}

repositories {
  maven("$rootDir/repo")
}


kotlin {
    listOf(linuxX64(), linuxArm64()).forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                create("yummy") {
                    defFile(project.file("$rootDir/nativeLib/yummy/yummy.def"))
                    includeDirs.allHeaders("$rootDir/nativeLib/yummy")
                    includeDirs.allHeaders("$rootDir/nativeLib/dummy")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.it:lib:1.0")
            }
        }
    }
}
