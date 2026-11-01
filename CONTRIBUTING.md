# Contributing

Use JDK 17 or newer. Run `sh scripts/test.sh` or `./scripts/test.ps1` before submitting changes. Run `mvn package` to verify the runnable JAR.

Keep scans read-only and runtime dependencies at zero. Include focused regression checks for behavior changes. For new options, document syntax, defaults, and failure behavior in the README.

When reporting a bug, include your operating system, Java version, command, and a small reproducible example. Remove private file paths and contents from reports.
