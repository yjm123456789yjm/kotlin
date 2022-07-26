#!/usr/bin/env bash
set -e # Exit if one of commands exit with non-zero exit code
set -u # Treat unset variables and parameters other than the special parameters ‘@’ or ‘*’ as an error
set -o pipefail # Any command failed in the pipe fails the whole pipe
# set -x # Print shell commands as they are executed (or you can try -v which is less verbose)

#!/bin/bash
            
            
            function runTest {
              local testName="Configuration cache for |'$1|'"
              echo "##teamcity[testStarted name='$testName']"
            
              local exitCode
              local gradleCommand="./gradlew $1 -Pkotlin.build.gradle.publish.javadocs=false --configuration-cache --dry-run"
              local storeEntryOutput
              storeEntryOutput=$($gradleCommand 2>&1)
              exitCode=$?
              echo "$storeEntryOutput"
              ([ $exitCode -eq 0 ] && echo "$storeEntryOutput" | grep "Configuration cache entry stored." > /dev/null) || (reportTestFailure "$testName" "Configuration cache is not stored" && return)
              local reuseEntryOutput
              reuseEntryOutput=$($gradleCommand 2>&1)
              exitCode=$?
              echo "$reuseEntryOutput"
              ([ $exitCode -eq 0 ] && echo "$reuseEntryOutput" | grep "Configuration cache entry reused." > /dev/null) || (reportTestFailure "$testName" "Configuration cache is not reused" && return)
            
              finishTest "$testName"
            }
            
            function reportTestFailure {
              if [ $? -ne 0 ]; then
                echo "##teamcity[testFailed name='$1' message='$2']"
                finishTest "$1"
              fi
            }
            
            function finishTest {
              echo "##teamcity[testFinished name='$1']"
            }
     
            runTest "clean"
runTest "install"
runTest "gradlePluginIntegrationTest"
runTest "miscCompilerTest"
runTest "jps-tests"
runTest "scriptingJvmTest"
runTest "generateTests"
