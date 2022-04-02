package org.jetbrains.kotlin.gradle.dsl

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinJvmOptionsTest {
    @Test
    fun testFreeArguments() {
        val project = ProjectBuilder.builder().build()
        val options = KotlinJvmOptionsBase(project.objects)
        options.freeCompilerArgs.addAll(
            listOf(
                "-Xreport-perf",
                "-Xallow-kotlin-package",
                "-Xmultifile-parts-inherit",
                "-Xdump-declarations-to", "declarationsPath",
                "-script-templates", "a,b,c"
            )
        )

        val arguments = K2JVMCompilerArguments()
        options.toCompilerArguments(arguments)
        assertEquals(options.freeCompilerArgs.get(), arguments.freeArgs)
    }
}