#!/usr/bin/env zsh
set -e # Exit if one of commands exit with non-zero exit code
set -u # Treat unset variables and parameters other than the special parameters ‘@’ or ‘*’ as an error
setopt +o nomatch
#set -x # Print shell commands as they are executed (or you can try -v which is less verbose)

# major=$(echo $version | perl -pe 's/^(\d+)\.(\d+)\.\d+$/\1.\2/')
version=$(git tag --points-at=@ | grep -P "v\d+(\.\d+){2}" | sed -E 's/.(.*)/\1/')
current_branch=$(git rev-parse --abbrev-ref @)
if [ $current_branch = "master" ]; then
    snapshotVersion=$(cat gradle.properties | grep defaultSnapshotVersion | sed -E 's/^defaultSnapshotVersion\=(.*)-SNAPSHOT/\1/')
    version=$snapshotVersion
fi


[ "$(echo $version | wc -l)" = 1 ] || { echo "Can't find/parse version" && exit 1; }

echo "Parsed version: $version"

rm -rf build/repo
rm -rf dist
./gradlew publish -PdeployVersion=$version
./gradlew distKotlinc -PdeployVersion=$version

test -d build/repo || { echo "Can't find build/repo maven repo" && exit 1; }
test -d dist/kotlinc/lib || { echo "Can't find dist" && exit 1; }

are_jars_equivalent() {
    rm -rf ~/jars_are_equal
    mkdir -p ~/jars_are_equal/foo
    mkdir -p ~/jars_are_equal/bar
    cp $1 ~/jars_are_equal/foo/foo.jar
    cp $2 ~/jars_are_equal/bar/bar.jar
    pushd ~/jars_are_equal
        pushd foo
            unzip foo.jar > /dev/null
            foo_md5=$(find . -name '*.class' | sort | xargs -l md5sum | awk '{print $1}' | md5sum | awk '{print $1}')
        popd
        pushd bar
            unzip bar.jar > /dev/null
            bar_md5=$(find . -name '*.class' | sort | xargs -l md5sum | awk '{print $1}' | md5sum | awk '{print $1}')
        popd
    popd

    test "$foo_md5" = "$bar_md5"
    return $?
}

exist_in_maven_central() {
    wget -S --spider https://repo1.maven.org/maven2/org/jetbrains/kotlin/$1/$version/${1}-$version.jar 2>&1 | grep -q 'HTTP/1.1 200 OK'
    return $?
}

get_artifact_id_by_dist_jar_name() {
    if echo "$1" | grep -q --fixed-string 'kotlinx-serialization-compiler-plugin.jar'; then
        echo kotlin-maven-serialization
    elif echo "$1" | grep -q --fixed-string 'sam-with-receiver-compiler-plugin.jar'; then
        echo kotlin-maven-sam-with-receiver
    elif echo "$1" | grep -q --fixed-string 'allopen-compiler-plugin.jar'; then
        echo kotlin-maven-allopen
    elif echo "$1" | grep -q --fixed-string 'lombok-compiler-plugin.jar'; then
        echo kotlin-maven-lombok
    elif echo "$1" | grep -q --fixed-string 'noarg-compiler-plugin.jar'; then
        echo kotlin-maven-noarg
    else
        echo "$1" | sed -E 's/^dist\/kotlinc\/lib\/(.*)\.jar$/\1/'
    fi
}

main() {
    find dist/kotlinc/lib -type f |
        while read it; do
            artifactId=$(get_artifact_id_by_dist_jar_name "$it")
            jar=$(echo build/repo/**/$artifactId/**/*${version}.jar)
            if echo "$it" | grep -q --fixed-string sources.jar; then
                echo "+ $it -> You can skip sources because they are not needed for the JPS"
            elif [ -f "$jar" ] && [ $(echo "$jar" | wc -l) = 1 ] && are_jars_equivalent "$it" "$jar"; then
                if exist_in_maven_central $artifactId; then
                    echo "+ $it -> $jar (exist in maven central)"
                elif [ $current_branch = "master" ]; then
                    echo "+ $it -> $jar"
                else
                    echo "- $it -> $jar (doesn't exist in maven central)"
                fi
            elif echo "$it" | grep -q parcelize-fir; then
                echo "+ $it -> you can ignore this fir compiler plugin (consulted with D. Novozhilov)";
            elif echo "$it" | grep -q trove4j; then
                echo "+ $it -> Put trove4j into into maven artifact pom";
            elif echo "$it" | grep -q --fixed-string "compiler-plugin.jar" && { exist_in_maven_central $artifactId || [ $current_branch = "master" ]; }; then
                echo "+ $it -> $artifactId"
            elif echo "$it" | grep -q --fixed-string 'mutability-annotations-compat.jar' && [ "$(als $it | grep class | wc -l)" = 2 ]; then
                echo "+ $it -> $artifactId"
            elif echo "$it" | grep -q --fixed-string 'kotlinx-coroutines-core-jvm'; then
                echo "+ $it -> org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm"
            elif echo "$it" | grep -q --fixed-string 'kotlin-ant'; then
                echo "+ $it -> kotlin-ant.jar isn't needed because it's a small noname jar (there is no even a module in kt project which depends on :kotlin-ant)"
            elif echo "$it" | grep -q --fixed-string 'js.engines.jar'; then
                echo "+ $it -> js.engines.jar isn't needed because it's a small noname jar"
            elif echo "$it" | grep -q --fixed-string 'annotations-13.0.jar'; then
                echo "+ $it -> jetbrains-annotations is available in maven central"
            elif echo "$it" | grep -q --fixed-string 'kotlin-runner.jar'; then
                echo "+ $it -> kotlin-runner isn't needed because it's a small noname jar (there is no even a module in kt project which depends on :kotlin-runner)"
            elif echo "$it" | grep -q --fixed-string 'parcelize'; then
                echo "+ $it -> parclize is an Android stuff, nobody uses it JPS (org.jetbrains.kotlin.parcelize.ir.ParcelizeIrTransformer also is not in the classpath)"
            elif echo "$it" | grep -q --fixed-string 'js.engines.jar'; then
                echo "+ $it -> not needed for JPS for sure"
            elif echo "$it" | grep -q --fixed-string 'kotlin-preloader.jar'; then
                echo "+ $it -> it's not needed in the compiler classpath (because I checked and org.jetbrains.kotlin.preloading.ClassPreloadingUtils isn't in the classpath)"
            elif echo "$it" | grep -q --fixed-string 'kotlin-annotation-processing-cli.jar'; then
                echo "+ $it -> it's not needed in the compiler classpath (because I checked and org.jetbrains.kotlin.kapt.cli.CliToolOption isn't in the classpath)"
            elif echo "$it" | grep -q --fixed-string 'android-extensions-compiler.jar'; then
                echo "+ $it -> it's not needed in the compiler classpath (because I checked and org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutGroup isn't in the classpath)"
            elif echo "$it" | grep -q --fixed-string 'android-extensions-runtime.jar'; then
                echo "+ $it -> it's not needed in the compiler classpath (because I checked and kotlinx.android.parcel.IgnoredOnParcel isn't in the classpath)"
            else
                echo "- $it -> Unknown"
            fi
        done #| sort # TODO uncomment
}

main

# ~/jb/kotlin/dist/kotlinc/lib 🚀 ls | while read it; do als $it 2> /dev/null | grep class$ | grep -v META-INF | head -1 | awk "{print \"$it \" \$4}"; done
# allopen-compiler-plugin.jar org/jetbrains/kotlin/allopen/AbstractAllOpenDeclarationAttributeAltererExtension$Companion.class
# android-extensions-compiler.jar org/jetbrains/kotlin/android/synthetic/diagnostic/ErrorsAndroid$1.class
# android-extensions-runtime.jar kotlinx/android/extensions/CacheImplementation$Companion.class
# annotations-13.0.jar org/intellij/lang/annotations/Flow.class
# js.engines.jar org/jetbrains/kotlin/js/engine/ProcessBasedScriptEngine.class
# jvm-abi-gen.jar org/jetbrains/kotlin/jvm/abi/AbiClassInfo$Public.class
# kotlin-annotation-processing-cli.jar org/jetbrains/kotlin/kapt/cli/CliToolOption$Format.class
# kotlin-annotation-processing.jar org/jetbrains/kotlin/kapt3/AbstractKapt3Extension$WhenMappings.class
# kotlin-annotation-processing-runtime.jar kotlinx/kapt/KaptIgnored.class
# kotlin-annotations-jvm.jar kotlin/annotations/jvm/MigrationStatus.class
# kotlin-ant.jar org/jetbrains/kotlin/ant/Kotlin2JsTask$fillSpecificArguments$1$1.class
# kotlin-compiler.jar com/google/common/annotations/Beta.class
# kotlin-daemon-client.jar org/jetbrains/kotlin/daemon/client/BasicCompilerServicesWithResultsFacadeServer.class
# kotlin-daemon.jar org/jetbrains/kotlin/daemon/CompileServiceImpl$gracefulShutdown$$inlined$schedule$1.class
# kotlin-imports-dumper-compiler-plugin.jar org/jetbrains/kotlin/importsDumper/ImportsDumperCliOptions.class
# kotlin-main-kts.jar org/jetbrains/kotlin/mainKts/impl/Directories.class
# kotlin-preloader.jar org/jetbrains/kotlin/preloading/ClassCondition.class
# kotlin-reflect.jar kotlin/reflect/full/IllegalCallableAccessException.class
# kotlin-runner.jar org/jetbrains/kotlin/runner/AbstractRunner.class
# kotlin-scripting-common.jar kotlin/script/experimental/annotations/KotlinScript.class
# kotlin-scripting-compiler-impl.jar org/jetbrains/kotlin/scripting/definitions/ScriptPriorities.class
# kotlin-scripting-compiler.jar org/jetbrains/kotlin/scripting/compiler/plugin/AbstractScriptEvaluationExtension$doEval$1.class
# kotlin-scripting-js.jar org/jetbrains/kotlin/scripting/repl/js/JsCompiledScript.class
# kotlin-scripting-jvm.jar kotlin/script/experimental/jvm/BasicJvmReplEvaluator$eval$1.class
# kotlin-script-runtime.jar kotlin/script/dependencies/BasicScriptDependenciesResolver.class
# kotlin-stdlib.jar kotlin/collections/ArraysUtilJVM.class
# kotlin-stdlib-jdk7.jar kotlin/internal/jdk7/JDK7PlatformImplementations$ReflectSdkVersion.class
# kotlin-stdlib-jdk8.jar kotlin/collections/jdk8/CollectionsJDK8Kt.class
# kotlin-test.jar kotlin/test/AssertContentEqualsImplKt$assertIterableContentEquals$1.class
# kotlin-test-junit5.jar kotlin/test/junit5/JUnit5Asserter.class
# kotlin-test-junit.jar kotlin/test/junit/JUnitAsserter.class
# kotlin-test-testng.jar kotlin/test/testng/TestNGAsserter.class
# kotlinx-coroutines-core-jvm.jar kotlinx/coroutines/JobNode.class
# kotlinx-serialization-compiler-plugin.jar org/jetbrains/kotlinx/serialization/compiler/diagnostic/SerializationErrors$1.class
# lombok-compiler-plugin.jar org/jetbrains/kotlin/lombok/LombokCommandLineProcessor$Companion.class
# mutability-annotations-compat.jar org/jetbrains/annotations/Mutable.class
# noarg-compiler-plugin.jar org/jetbrains/kotlin/noarg/diagnostic/ErrorsNoArg$1.class
# parcelize-compiler.jar org/jetbrains/kotlin/parcelize/diagnostic/ErrorsParcelize$1.class
# parcelize-fir.jar org/jetbrains/kotlin/parcelize/ParcelizeFirIrGeneratorExtension.class
# parcelize-runtime.jar kotlinx/parcelize/IgnoredOnParcel.class
# sam-with-receiver-compiler-plugin.jar org/jetbrains/kotlin/samWithReceiver/CliSamWithReceiverComponentContributor.class
# trove4j.jar gnu/trove/TDoubleDoubleHashMap$EqProcedure.class

# allopen-compiler-plugin.jar org/jetbrains/kotlin/allopen/AbstractAllOpenDeclarationAttributeAltererExtension
# android-extensions-compiler.jar org/jetbrains/kotlin/android/synthetic/diagnostic/ErrorsAndroid
# android-extensions-runtime.jar kotlinx/android/extensions/CacheImplementation
# annotations-13.0.jar org/intellij/lang/annotations/Flow
# js.engines.jar org/jetbrains/kotlin/js/engine/ProcessBasedScriptEngine
# jvm-abi-gen.jar org/jetbrains/kotlin/jvm/abi/AbiClassInfo
# kotlin-annotation-processing-cli.jar org/jetbrains/kotlin/kapt/cli/CliToolOption
# kotlin-annotation-processing.jar org/jetbrains/kotlin/kapt3/AbstractKapt3Extension
# kotlin-annotation-processing-runtime.jar kotlinx/kapt/KaptIgnored
# kotlin-annotations-jvm.jar kotlin/annotations/jvm/MigrationStatus
# kotlin-ant.jar org/jetbrains/kotlin/ant/Kotlin2JsTask
# kotlin-compiler.jar com/google/common/annotations/Beta
# kotlin-daemon-client.jar org/jetbrains/kotlin/daemon/client/BasicCompilerServicesWithResultsFacadeServer
# kotlin-daemon.jar org/jetbrains/kotlin/daemon/CompileServiceImpl
# kotlin-imports-dumper-compiler-plugin.jar org/jetbrains/kotlin/importsDumper/ImportsDumperCliOptions
# kotlin-main-kts.jar org/jetbrains/kotlin/mainKts/impl/Directories.class
# kotlin-preloader.jar org/jetbrains/kotlin/preloading/ClassCondition
# kotlin-reflect.jar kotlin/reflect/full/IllegalCallableAccessException
# kotlin-runner.jar org/jetbrains/kotlin/runner/AbstractRunner
# kotlin-scripting-common.jar kotlin/script/experimental/annotations/KotlinScript
# kotlin-scripting-compiler-impl.jar org/jetbrains/kotlin/scripting/definitions/ScriptPriorities
# kotlin-scripting-compiler.jar org/jetbrains/kotlin/scripting/compiler/plugin/AbstractScriptEvaluationExtension
# kotlin-scripting-js.jar org/jetbrains/kotlin/scripting/repl/js/JsCompiledScript
# kotlin-scripting-jvm.jar kotlin/script/experimental/jvm/BasicJvmReplEvaluator
# kotlin-script-runtime.jar kotlin/script/dependencies/BasicScriptDependenciesResolver
# kotlin-stdlib.jar kotlin/collections/ArraysUtilJVM
# kotlin-stdlib-jdk7.jar kotlin/internal/jdk7/JDK7PlatformImplementations
# kotlin-stdlib-jdk8.jar kotlin/collections/jdk8/CollectionsJDK8Kt
# kotlin-test.jar kotlin/test/AssertContentEqualsImplKt
# kotlin-test-junit5.jar kotlin/test/junit5/JUnit5Asserter
# kotlin-test-junit.jar kotlin/test/junit/JUnitAsserter
# kotlin-test-testng.jar kotlin/test/testng/TestNGAsserter
# kotlinx-coroutines-core-jvm.jar kotlinx/coroutines/JobNode
# kotlinx-serialization-compiler-plugin.jar org/jetbrains/kotlinx/serialization/compiler/diagnostic/SerializationErrors
# lombok-compiler-plugin.jar org/jetbrains/kotlin/lombok/LombokCommandLineProcessor
# mutability-annotations-compat.jar org/jetbrains/annotations/Mutable
# noarg-compiler-plugin.jar org/jetbrains/kotlin/noarg/diagnostic/ErrorsNoArg
# parcelize-compiler.jar org/jetbrains/kotlin/parcelize/diagnostic/ErrorsParcelize
# parcelize-fir.jar org/jetbrains/kotlin/parcelize/ParcelizeFirIrGeneratorExtension
# parcelize-runtime.jar kotlinx/parcelize/IgnoredOnParcel
# sam-with-receiver-compiler-plugin.jar org/jetbrains/kotlin/samWithReceiver/CliSamWithReceiverComponentContributor
# trove4j.jar gnu/trove/TDoubleDoubleHashMap

# Classes in the classpath (+ is in the classpath; - NOT in the classpath)
# - org.jetbrains.kotlin.allopen.AbstractAllOpenDeclarationAttributeAltererExtension                allopen-compiler-plugin.jar org.jetbrains.kotlin.allopen.AbstractAllOpenDeclarationAttributeAltererExtension
# - org.jetbrains.kotlin.android.synthetic.diagnostic.ErrorsAndroid                                 android-extensions-compiler.jar org.jetbrains.kotlin.android.synthetic.diagnostic.ErrorsAndroid
# - kotlinx.android.extensions.CacheImplementation                                                  android-extensions-runtime.jar kotlinx.android.extensions.CacheImplementation
# + org.intellij.lang.annotations.Flow                                                              annotations-13.0.jar org.intellij.lang.annotations.Flow
# - org.jetbrains.kotlin.js.engine.ProcessBasedScriptEngine                                         js.engines.jar org.jetbrains.kotlin.js.engine.ProcessBasedScriptEngine
# - org.jetbrains.kotlin.jvm.abi.AbiClassInfo                                                       jvm-abi-gen.jar org.jetbrains.kotlin.jvm.abi.AbiClassInfo
# - org.jetbrains.kotlin.kapt.cli.CliToolOption                                                     kotlin-annotation-processing-cli.jar org.jetbrains.kotlin.kapt.cli.CliToolOption
# - org.jetbrains.kotlin.kapt3.AbstractKapt3Extension                                               kotlin-annotation-processing.jar org.jetbrains.kotlin.kapt3.AbstractKapt3Extension
# - kotlinx.kapt.KaptIgnored                                                                        kotlin-annotation-processing-runtime.jar kotlinx.kapt.KaptIgnored
# - kotlin.annotations.jvm.MigrationStatus                                                          kotlin-annotations-jvm.jar kotlin.annotations.jvm.MigrationStatus
# - org.jetbrains.kotlin.ant.Kotlin2JsTask                                                          kotlin-ant.jar org.jetbrains.kotlin.ant.Kotlin2JsTask
# + com.google.common.annotations.Beta                                                              kotlin-compiler.jar com.google.common.annotations.Beta
# - org.jetbrains.kotlin.daemon.client.BasicCompilerServicesWithResultsFacadeServer                 kotlin-daemon-client.jar org.jetbrains.kotlin.daemon.client.BasicCompilerServicesWithResultsFacadeServer
# + org.jetbrains.kotlin.daemon.CompileServiceImpl                                                  kotlin-daemon.jar org.jetbrains.kotlin.daemon.CompileServiceImpl
# - org.jetbrains.kotlin.importsDumper.ImportsDumperCliOptions                                      kotlin-imports-dumper-compiler-plugin.jar org.jetbrains.kotlin.importsDumper.ImportsDumperCliOptions
# - org.jetbrains.kotlin.mainKts.impl.Directories                                                   kotlin-main-kts.jar org.jetbrains.kotlin.mainKts.impl.Directories
# - org.jetbrains.kotlin.preloading                                                                 kotlin-preloader.jar org.jetbrains.kotlin.preloading.ClassCondition
# + kotlin.reflect.full.IllegalCallableAccessException                                              kotlin-reflect.jar kotlin.reflect.full.IllegalCallableAccessException
# - org.jetbrains.kotlin.runner.AbstractRunner                                                      kotlin-runner.jar org.jetbrains.kotlin.runner.AbstractRunner
# - kotlin.script.experimental.annotations.KotlinScript                                             kotlin-scripting-common.jar kotlin.script.experimental.annotations.KotlinScript
# - org.jetbrains.kotlin.scripting.definitions.ScriptPriorities                                     kotlin-scripting-compiler-impl.jar org.jetbrains.kotlin.scripting.definitions.ScriptPriorities
# - org.jetbrains.kotlin.scripting.compiler.plugin.AbstractScriptEvaluationExtension                kotlin-scripting-compiler.jar org.jetbrains.kotlin.scripting.compiler.plugin.AbstractScriptEvaluationExtension
# - org.jetbrains.kotlin.scripting.repl.js.JsCompiledScript                                         kotlin-scripting-js.jar org.jetbrains.kotlin.scripting.repl.js.JsCompiledScript
# - kotlin.script.experimental.jvm.BasicJvmReplEvaluator                                            kotlin-scripting-jvm.jar kotlin.script.experimental.jvm.BasicJvmReplEvaluator
# + kotlin.script.dependencies.BasicScriptDependenciesResolver                                      kotlin-script-runtime.jar kotlin.script.dependencies.BasicScriptDependenciesResolver
# + kotlin.collections.ArraysUtilJVM                                                                kotlin-stdlib.jar kotlin.collections.ArraysUtilJVM
# - kotlin.internal.jdk7.JDK7PlatformImplementations                                                kotlin-stdlib-jdk7.jar kotlin.internal.jdk7.JDK7PlatformImplementations
# - kotlin.collections.jdk8.CollectionsJDK8Kt                                                       kotlin-stdlib-jdk8.jar kotlin.collections.jdk8.CollectionsJDK8Kt
# - kotlin.test.AssertContentEqualsImplKt                                                           kotlin-test.jar kotlin.test.AssertContentEqualsImplKt
# - kotlin.test.junit5.JUnit5Asserter                                                               kotlin-test-junit5.jar kotlin.test.junit5.JUnit5Asserter
# - kotlin.test.junit.JUnitAsserter                                                                 kotlin-test-junit.jar kotlin.test.junit.JUnitAsserter
# - kotlin.test.testng.TestNGAsserter                                                               kotlin-test-testng.jar kotlin.test.testng.TestNGAsserter
# - kotlinx.coroutines.JobNode                                                                      kotlinx-coroutines-core-jvm.jar kotlinx.coroutines.JobNode
# - org.jetbrains.kotlinx.serialization.compiler.diagnostic.SerializationErrors                     kotlinx-serialization-compiler-plugin.jar org.jetbrains.kotlinx.serialization.compiler.diagnostic.SerializationErrors
# - org.jetbrains.kotlin.lombok.LombokCommandLineProcessor                                          lombok-compiler-plugin.jar org.jetbrains.kotlin.lombok.LombokCommandLineProcessor
# - org.jetbrains.annotations.Mutable                                                               mutability-annotations-compat.jar org.jetbrains.annotations.Mutable
# - org.jetbrains.kotlin.noarg.diagnostic.ErrorsNoArg                                               noarg-compiler-plugin.jar org.jetbrains.kotlin.noarg.diagnostic.ErrorsNoArg
# - org.jetbrains.kotlin.parcelize.diagnostic.ErrorsParcelize                                       parcelize-compiler.jar org.jetbrains.kotlin.parcelize.diagnostic.ErrorsParcelize
# - org.jetbrains.kotlin.parcelize.ParcelizeFirIrGeneratorExtension                                 parcelize-fir.jar org.jetbrains.kotlin.parcelize.ParcelizeFirIrGeneratorExtension
# - kotlinx.parcelize.IgnoredOnParcel                                                               parcelize-runtime.jar kotlinx.parcelize.IgnoredOnParcel
# - org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor                     sam-with-receiver-compiler-plugin.jar org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor
# + gnu.trove.TDoubleDoubleHashMap                                                                  trove4j.jar gnu.trove.TDoubleDoubleHashMap
