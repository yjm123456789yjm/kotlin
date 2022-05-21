// FILE: kotlin.kt
package kotlin.native

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class RefinesForObjC

@RefinesForObjC
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RefinedForObjC

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RefinesInSwift

@RefinesInSwift
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class RefinedInSwift

// FILE: plugin.kt
package plugin

@RefinesForObjC
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PluginRefinedForObjC

@RefinesInSwift
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PluginRefinedInSwift

// FILE: test.kt
import plugin.PluginRefinedForObjC
import plugin.PluginRefinedInSwift

@RefinesForObjC
<!REDUNDANT_SWIFT_REFINEMENT!>@RefinesInSwift<!>
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class MyRefinedAnnotationA

<!INVALID_OBJC_REFINEMENT_TARGETS!>@RefinesForObjC<!>
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class MyRefinedAnnotationB

<!INVALID_OBJC_REFINEMENT_TARGETS!>@RefinesInSwift<!>
@Retention(AnnotationRetention.BINARY)
annotation class MyRefinedAnnotationC

@RefinedForObjC
<!REDUNDANT_SWIFT_REFINEMENT!>@RefinedInSwift<!>
var refinedProperty: Int = 0

@PluginRefinedForObjC
<!REDUNDANT_SWIFT_REFINEMENT!>@PluginRefinedInSwift<!>
fun pluginRefinedFunction() { }

@RefinedForObjC
@PluginRefinedForObjC
fun multipleObjCRefinementsFunction() { }

@RefinedInSwift
@PluginRefinedInSwift
fun multipleSwiftRefinementsFunction() { }

@RefinedForObjC
@PluginRefinedForObjC
<!REDUNDANT_SWIFT_REFINEMENT!>@RefinedInSwift<!>
<!REDUNDANT_SWIFT_REFINEMENT!>@PluginRefinedInSwift<!>
fun multipleMixedRefinementsFunction() { }

interface InterfaceA {
    val barA: Int
    val barB: Int
    fun fooA()
    @RefinedForObjC
    fun fooB()
}

interface InterfaceB {
    val barA: Int
    @RefinedInSwift
    val barB: Int
    @RefinedForObjC
    fun fooA()
    @RefinedForObjC
    fun fooB()
}

open class ClassA: InterfaceA, InterfaceB {
    <!INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE!>@RefinedForObjC<!>
    override val barA: Int = 0
    <!INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE!>@RefinedInSwift<!>
    override val barB: Int = 0
    <!INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE!>override fun fooA() { }<!>
    override fun fooB() { }
    @RefinedForObjC
    open fun fooC() { }
}

class ClassB: ClassA() {
    @RefinedForObjC
    override fun fooB() { }
    <!INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE!>@RefinedInSwift<!>
    override fun fooC() { }
}
