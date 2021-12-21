plugins {
    kotlin("multiplatform")
}

kotlin {
    linuxX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

kotlinArtifacts {
    Native.Library("mylib") {
        target = linuxX64
    }
    Native.Library("myslib") {
        target = linuxX64
        modes(DEBUG)
        addModule(project(":lib"))
    }
    Native.XCFramework {
        targets(iosX64, iosArm64, iosSimulatorArm64)
        setModules(
            project(":shared"),
            project(":lib")
        )
    }
}