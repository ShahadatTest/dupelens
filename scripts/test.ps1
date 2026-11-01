$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force build | Out-Null
javac -encoding UTF-8 -d build src/main/java/dev/dupelens/Main.java src/test/java/dev/dupelens/SmokeTest.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp build dev.dupelens.SmokeTest
exit $LASTEXITCODE
