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
annotation class MyRefinedAnnotation

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
