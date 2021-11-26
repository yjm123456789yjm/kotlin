/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import kotlin.test.Test

class NativeLibraryDslIT : BaseGradleIT() {
    override val defaultGradleVersion = GradleVersionRequired.FOR_MPP_SUPPORT

    @Test
    fun `check registered gradle tasks`() {
        with(Project("new-kn-library-dsl")) {
            build(":shared:tasks") {
                assertSuccessful()
                assertTasksRegistered(
                    ":shared:assembleMylibDebugSharedLibraryLinuxX64",
                    ":shared:assembleMyslibDebugStaticLibraryLinuxX64",
                    ":shared:assembleMylibReleaseSharedLibraryLinuxX64",
                    ":shared:assembleMylibSharedLibrary",
                    ":shared:assembleMyslibStaticLibrary"
                )
                assertTasksNotRegistered(
                    ":shared:assembleMyslibReleaseStaticLibraryLinuxX64"
                )
            }
        }
    }

    @Test
    fun `link static library from two gradle modules`() {
        with(Project("new-kn-library-dsl")) {
            build(":shared:assembleMyslibDebugStaticLibraryLinuxX64") {
                assertSuccessful()
                assertTasksExecuted(
                    ":lib:compileKotlinLinuxX64",
                    ":shared:compileKotlinLinuxX64",
                    ":shared:assembleMyslibDebugStaticLibraryLinuxX64"
                )
                assertFileExists("/shared/build/out/static/linux_x64/debug/libmyslib.a")
                assertFileExists("/shared/build/out/static/linux_x64/debug/libmyslib_api.h")
            }
        }
    }

    @Test
    fun `link shared library from single gradle module`() {
        with(Project("new-kn-library-dsl")) {
            build(":shared:assembleMylibDebugSharedLibraryLinuxX64") {
                assertSuccessful()
                assertTasksExecuted(
                    ":shared:compileKotlinLinuxX64",
                    ":shared:assembleMylibDebugSharedLibraryLinuxX64"
                )
                assertTasksNotExecuted(
                    ":lib:compileKotlinLinuxX64"
                )
                assertFileExists("/shared/build/out/dynamic/linux_x64/debug/libmylib.so")
                assertFileExists("/shared/build/out/dynamic/linux_x64/debug/libmylib_api.h")
            }
        }
    }
}