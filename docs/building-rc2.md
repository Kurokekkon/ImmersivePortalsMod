# Building this fork

Use Java 25 and the included Gradle wrapper. The published RC2 source was assembled with this explicit Fabric API override:

```sh
./gradlew assemble -Pfabric_version=0.164.0+26.3 --no-daemon --console=plain
```

On Windows, use `gradlew.bat`. The normal mod artifact is written to `build/libs`. Do not install the separately built test drivers in a normal server or client.

The `mod_version` remains 6.0.6 from the base port. The RC2 filename identifies the tested candidate; this publication does not change runtime version metadata or the existing tested binary.

Optional renderer dependencies are compile-only by default. See the individual phase reports for exact test commands and versions; compatibility claims are limited to those environments.

The scripts in `tools/phase6-*` and `tools/phase81-*` are historical, Windows-specific integration harnesses. They depend on cached development launches, marked disposable server copies and documented local paths. They are not general installation scripts and should not be run against a live server. Read and adapt their paths and isolation requirements before use. The ordinary `assemble` command does not start these servers or copy production worlds.

Source publication checks included comparison of every runtime source/resource file against the tested RC2 source manifest, Git whitespace checks and PowerShell parsing of the publication-adjusted helper. No gameplay tests were rerun just for publication.

Original upstream README: [upstream-readme.md](upstream-readme.md). The current README describes this fork's supported scope.
