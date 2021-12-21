#!/bin/sh
set -e -x -u

#cat <<EOF > ./local.properties
#kotlin.build.isObsoleteJdkOverrideEnabled=true
#EOF

./gradlew assemble dist
