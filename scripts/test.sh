#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
javac -encoding UTF-8 -d build src/main/java/dev/dupelens/Main.java src/test/java/dev/dupelens/SmokeTest.java
java -cp build dev.dupelens.SmokeTest
