#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
javac -encoding UTF-8 -d build src/main/java/dev/dupelens/Main.java src/main/java/dev/dupelens/Review.java src/test/java/dev/dupelens/SmokeTest.java src/test/java/dev/dupelens/ReviewTest.java
java -cp build dev.dupelens.SmokeTest
java -cp build dev.dupelens.ReviewTest
