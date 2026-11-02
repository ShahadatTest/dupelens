$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force build | Out-Null
javac -encoding UTF-8 -d build src/main/java/dev/dupelens/Main.java src/main/java/dev/dupelens/Review.java src/test/java/dev/dupelens/SmokeTest.java src/test/java/dev/dupelens/ReviewTest.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp build dev.dupelens.SmokeTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp build dev.dupelens.ReviewTest
exit $LASTEXITCODE
