# covdbg for CLion

Measure C++ code coverage in CLion with [covdbg](https://covdbg.com), which instruments a running
process directly rather than requiring an instrumented build.

Run any CLion run configuration under covdbg and see the result in CLion's own coverage UI: the
editor gutter (covered, partially covered, not covered, with hit counts), the Coverage tool window
with per-directory and per-file percentages, *Show Coverage Data* for earlier runs, and *Import
External Coverage Report* for any `.covdb`. Export an offline HTML report from *Tools | covdbg* or
the *Run* menu's coverage section.

A line is *partially covered* when some of the basic blocks covdbg mapped to it ran and others did
not - an `if` whose branch was never taken, say. The line's execution count alone cannot show that:
covdbg records it as the sum of its blocks' hits.

## Requirements

| | |
| --- | --- |
| CLion | 2025.3 through 2026.2 |
| Platform | Windows only |
| Toolchain | MSVC (Visual Studio). WSL, MinGW and Cygwin profiles are not supported and the covdbg executor hides itself for them |
| covdbg | **1.3.0 or newer**, installed separately — this plugin drives `covdbg.exe`, it does not bundle it. Found on `PATH` automatically |
| Licensing | A signed-in seat, or `COVDBG_PROJECT_TOKEN` in the environment |
| Configuration | A `.covdbg.yaml` in the repository |

Earlier covdbg releases will not work: 1.3.0 removed the `--appdata`, `--license`, `--license-file`
and `--fetch-license` options this plugin used to pass, and replaced them with seat licensing. When
the installed covdbg is too old, a run fails with covdbg rejecting an option, and the plugin says so.

## Getting started

1. **Check that covdbg is found.** Nothing to do if covdbg is on `PATH`, which its installer and the
   winget package arrange — the plugin looks there, then at the usual install locations
   (`%ProgramFiles%\Liasoft\covdbg`, `%LOCALAPPDATA%\Programs\covdbg`). *Settings | Build,
   Execution, Deployment | Covdbg* shows which executable was picked and what version it reported.
   Set *Covdbg path* only to override that — for instance to test a locally built covdbg.
2. **Sign in.** *Tools | covdbg | Sign In to covdbg*, or click the covdbg status bar widget, which
   shows who is signed in. covdbg prints a URL and a code; the plugin shows them in a notification with buttons to
   open the page and copy the code. Confirm in the browser and the sign-in completes — allow a few
   seconds, since covdbg polls rather than waiting on a socket. Cancelling the background task is
   safe; nothing is stored and the code expires on its own.

   In CI, set `COVDBG_PROJECT_TOKEN` instead. Note that `covdbg whoami` does not read that variable,
   so the plugin reports *Using COVDBG_PROJECT_TOKEN from the environment* rather than claiming
   nobody is signed in.
3. **Create a configuration.** *Tools | covdbg | Create .covdbg.yaml* writes a starter file in the
   project root. It is meant to be committed and edited: the include and exclude patterns decide what
   is measured. Without one, covdbg refuses the run.
4. **Run.** Pick *Run 'target' with Covdbg* from the run-configuration dropdown, or create a covdbg
   run configuration directly.

## What a run does

One covdbg invocation, writing `.covdbg/<target>.covdb` under the project root.

covdbg analyses the target binary before running it, so functions and lines the run never executes
already appear with zero hits — there is no separate analysis pass to wait for. Code compiled into a
binary the run never loads is the exception: a static library that no test binary fully links is
absent from the report rather than shown as uncovered. List such binaries under `baseline:` in
`.covdbg.yaml` and covdbg folds them into the run. The starter configuration documents this.

Results are reported from covdbg's exit code and output, never from whether a database file happens
to exist:

| What you see | What happened |
| --- | --- |
| Coverage percentages | Success; the database is loaded |
| *Coverage reporting is gated for this account* | The run was allowed but the database keeps only the ten most-hit files, so the totals do not describe the whole program. It is still loaded, and the notification says so |
| *This run is not licensed* | Refused. No database was written. Sign in, or set `COVDBG_PROJECT_TOKEN` |
| *No functions passed the coverage filter* | The filters in `.covdbg.yaml` matched nothing |
| *No .covdbg.yaml found* | Create one; the notification offers to |
| *covdbg rejected an option* | The installed covdbg is probably older than 1.3.0 |
| *covdbg was not found* | Not on `PATH` or at a known install location; install it or set *Covdbg path* |

## Settings

Project-level, under *Settings | Build, Execution, Deployment | Covdbg*.

| Setting | Effect |
| --- | --- |
| Covdbg path | Overrides automatic lookup. Empty means `PATH`, then the known install locations |
| Default config path | `--config`. Empty lets covdbg discover `.covdbg.yaml` from the working directory |
| Log level | `--log-level` |
| Log file | `--log-file`. Empty uses covdbg's default, `.covdbg/Logs/covdbg.log` in the working directory |
| Symbol engine | `--symbol-engine`. Empty uses covdbg's default |
| Follow child processes when running CLion configurations | `--follow-children` for *Run with covdbg* on a CLion run configuration. covdbg run configurations have their own checkbox |

Telemetry is covdbg's own setting, not the plugin's: set `COVDBG_TELEMETRY=off` in the environment or
`telemetry: false` in `.covdbg.yaml`.

Note that the covdbg path is stored per project, so each project is configured separately.

## Building

Gradle itself runs on any recent JDK - the one bundled with CLion will do - and fetches the Java 21
toolchain it compiles with on first use:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\CLion\jbr"

.\gradlew.bat test           # unit tests
.\gradlew.bat verifyPlugin   # Marketplace compatibility checks
.\gradlew.bat buildPlugin    # distribution zip under build/distributions
.\gradlew.bat runIde         # sandbox CLion (oldest supported) with the plugin installed
.\gradlew.bat runIdeNewest   # same, in the newest supported CLion
```

`platformVersion` is the **oldest** supported platform and is what the plugin compiles against, so an
API added in a later release cannot be used by accident. Do not raise it to try the plugin on a newer
CLion — use `runIdeNewest`, which loads the built artifact into `verifyAgainstVersion`. That is both
the faithful test (it is what a user on that release installs) and the only one that works: the 2026.2
platform classes carry Kotlin 2.4 metadata, which the Kotlin version pinned here cannot read, so
compiling against them fails outright.

The build compiles on Java 21 and targets Java 21, because that is what the oldest supported CLion
runs on. Current JetBrains IDEs bundle JBR 25, so that toolchain usually is not present; the
`foojay-resolver-convention` plugin in `settings.gradle.kts` lets Gradle download it rather than
failing to configure. Compiling on a newer JDK instead is not a workaround - Kotlin then derives a JVM
target it does not recognise.

The tests are plain JUnit 5 and start no IDE. That is deliberate: argument building, covdbg output
parsing, `.covdb` reading and version comparison are kept free of `Project` and EDT dependencies so
they can be tested directly, which is why the IntelliJ platform test framework is not a dependency
here.

### Troubleshooting the build

**"The contents of the immutable workspace ... have been modified"** on any Gradle task. This reads
like disk corruption, but it is CLion's bundled GDB: its embedded CPython writes `__pycache__/*.pyc`
next to its Python support modules, which live inside the IDE distribution Gradle extracted into its
transform cache and treats as immutable. The build sets `PYTHONDONTWRITEBYTECODE=1` for tasks that
launch the IDE, so it should not recur; if something else starts that GDB, delete the added files
rather than the multi-gigabyte cache entry:

```powershell
$cache = "$env:USERPROFILE\.gradle\caches\9.0.0\transforms\<hash>\transformed"
Get-ChildItem $cache -Recurse -Directory -Filter __pycache__ | Remove-Item -Recurse -Force
```

The `<hash>` is named in the error message. Only the added `.pyc` files need to go; removing them
restores the tree Gradle hashed.

## Contributing

Issues and pull requests are welcome at
[liasoft/covdbg-clion](https://github.com/liasoft/covdbg-clion). This repository contains only the
CLion plugin; covdbg itself is a separate product, so problems with covdbg's own behaviour
(instrumentation, licensing, the `.covdb` format) belong with [covdbg](https://covdbg.com) rather
than here. Before opening a pull request, run `.\gradlew.bat test verifyPlugin` (see *Building*).
The notes below explain design decisions that look odd at first sight.

"Run with covdbg" on a CLion run configuration does not build a run of its own. `CovdbgCoverageRunner`
runs the configuration's own state, exactly as CLion's Run does, and
`run/CovdbgRunConfigurationExtension.kt` — a `CidrRunConfigurationExtensionBase` registered under
`cidr.runConfigurationExtension` — puts covdbg in front of the target while CLion's launcher builds
the command line. Valgrind is integrated into CLion the same way. Everything a normal run does is
therefore CLion's: build before launch, the toolchain environment, macros, environment variables,
input redirection, terminal emulation, elevation, test filters, and the test tree with rerun of
failed tests. The extension only swaps the executable and moves the target behind covdbg's options;
it acts only when the runner id is `CovdbgCoverageRunner`, so ordinary runs never see it. A
configuration that is not launched through that extension point is stopped and reported, rather
than left running without covdbg.

The extension is handed the `CPPEnvironment`, so it refuses non-MSVC toolchains with the exact
`isMSVC()` check. `canRun`, which decides whether the executor is offered at all, has no environment,
so `run/CmakeProfileName.kt` reads the CMake profile name instead, and two members are reached
reflectively for it. The reason is worth knowing before anyone "fixes" it:

CLion declares them on its CMake run-configuration classes. In 2025.3 those live in the CMake
plugin's main jar; in **2026.2 they moved into the content module `intellij.cmake.core`, declared
`visibility="internal"` and absent from the plugin's classpath**. So a typed
`CMakeAppRunConfiguration` cast compiles, passes `verifyPlugin` as *Compatible*, and then throws
`NoClassDefFoundError` on 2026.2. Reflecting on the instance never names the class, so it works on
both; the signatures were checked identical in 253 and 262. Avoid
`CMakeAppRunConfiguration.getType()` (covariant override removed in 262).

`untilBuild` is bounded rather than open, so a platform that has not been looked at is refused
instead of silently misbehaving — widening the range is a deliberate act.

Internal platform API fails the build, with one listed exception. The platform coverage gutter
finds a file's coverage only through `CoverageEngine.getQualifiedName(File, PsiFile)`, which is
internal, so `CovdbgCoverageEngine` overrides it, as CLion's own coverage engine does.
`verifier-allowed-internal-api.txt` allows exactly that, and `checkInternalApiUsages` (run after
`verifyPlugin`) fails on any other internal usage the verifier reports.

`verifyPlugin` checks both ends of the supported range (`platformVersion` and `verifyAgainstVersion`
in `gradle.properties`). It only proves API compatibility — it reported *Compatible* for the typed
approach described above, which would have broken — so when widening the range also confirm the
reflected members still exist in the new platform's jars, then run the real thing at both ends:

```powershell
.\gradlew.bat runIde        # oldest supported
.\gradlew.bat runIdeNewest  # newest supported
# in each, on a project with two CMake profiles (Debug and Release):
#   1. "Run 'target' with Covdbg" under Debug  -> the Debug binary is launched, coverage loads
#   2. switch to Release and run again         -> the *Release* binary is launched
#   3. run a single Google Test                -> the command line carries --gtest_filter=
#   4. delete the build directory and run      -> an explicit error, not a stale path
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
