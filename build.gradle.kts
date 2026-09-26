import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.covdbg"

/** The Java version the oldest supported CLion runs on: both the toolchain and the bytecode target. */
val JVM_TARGET = 21
// Derived from git by GitVersion (GitVersion.yml) and passed in by CI as -PpluginVersion. A local build
// without it is marked as one rather than posing as a release.
version = providers.gradleProperty("pluginVersion").getOrElse("0.0.0-dev")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        clion(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.clion")

        // The platform coverage UI (engine, suites, gutter, Coverage tool window). A platform module,
        // not a bundled plugin; the agent module carries ProjectData/ClassData/LineData.
        bundledModule("intellij.platform.coverage")
        bundledModule("intellij.platform.coverage.agent")

        // No testFramework(...) on purpose: see the test dependencies below.
    }
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    // The plugin's own logic (argv building, CLI output parsing, .covdb reading) is deliberately
    // free of Project/EDT dependencies so it can be tested without an IDE fixture.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // The IntelliJ Platform Gradle Plugin runs the test task under the platform's own class loader,
    // which resolves JUnit 4 types while starting up. The tests themselves are JUnit 5; this is only
    // here so that loader can initialise.
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")

            // Bounded on purpose. CmakeProfileName still reflects for the members CLion declares
            // on CMake types, which a third-party plugin cannot name on 2026.2, and the run hooks
            // into CLion's run-configuration extension point; an open range would turn a change
            // there into silent "covdbg does nothing" reports instead of an explicit compatibility
            // bump.
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        changeNotes = """
            <h3>1.3.0</h3>
            <ul>
              <li>Support covdbg 1.3.0. Earlier covdbg releases are no longer compatible.</li>
              <li>covdbg is now found automatically on <code>PATH</code>, or at its usual install
                  locations, so the Covdbg path setting is only needed to override that.</li>
              <li>Supports CLion 2025.3 through 2026.2.</li>
              <li>Seat licensing: sign in and out of covdbg from Tools | covdbg or the status bar,
                  which shows the signed-in account; the settings panel shows it too. The removed
                  <code>--license</code>, <code>--license-file</code>, <code>--fetch-license</code>
                  and <code>--appdata</code> options are gone, along with their settings fields.</li>
              <li>A coverage run is now a single covdbg invocation. The separate analyze and merge
                  steps are obsolete: covdbg analyses the target before running it, so
                  never-executed code already appears with zero hits, and code in an unlinked static
                  library is covered by <code>baseline:</code> in <code>.covdbg.yaml</code>.</li>
              <li>Run results are reported from covdbg's exit code and output rather than from
                  whether a database file happens to exist, so a refused run no longer shows the
                  previous run's coverage.</li>
              <li>Gated reports are detected from covdbg's own output and labelled in the run's
                  notification.</li>
              <li>New options: follow child processes, symbol engine, and a custom log file.</li>
              <li>Create a starter <code>.covdbg.yaml</code> when a run has no configuration.</li>
              <li>"Run with covdbg" runs the configuration itself and puts covdbg in front of the
                  target, so it behaves as a normal run: the test tree and rerunning failed tests,
                  building before launch, the toolchain environment, macros, environment variables,
                  input redirection, terminal emulation and administrator privileges.</li>
              <li>Coverage is shown through CLion's own coverage UI: the editor gutter, now with
                  partially covered lines, the Coverage tool window, Show Coverage Data for earlier
                  runs, and Import External Coverage Report for <code>.covdb</code> files. The
                  separate covdbg tool window, the display-mode widget and its settings are gone.</li>
              <li>Export an offline HTML coverage report.</li>
              <li>The detected covdbg version is shown in the settings panel.</li>
            </ul>
            <p>Settings saved by earlier versions keep a few now-unused entries in
            <code>covdbg.xml</code> until settings are next applied. They are ignored.</p>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            // Both ends of the supported range, not `recommended()`, which only picks one build.
            // A range is a claim about every IDE in it, and this plugin reaches into CLion internals,
            // so the claim needs checking at each end.
            create(IntelliJPlatformType.CLion, providers.gradleProperty("platformVersion"))
            create(IntelliJPlatformType.CLion, providers.gradleProperty("verifyAgainstVersion"))
        }

        // The defaults minus INTERNAL_API_USAGES, which checkInternalApiUsages enforces instead: it
        // fails on any internal usage except those listed in verifier-allowed-internal-api.txt. The
        // verifier's own -ignored-problems does not apply to internal API findings.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES
        )
    }

    buildSearchableOptions = false

    // Only signPlugin and publishPlugin read these; the publish job in .github/workflows/build.yml
    // supplies them from repository secrets.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // A pre-release such as 1.4.0-beta.1 goes to the Marketplace channel named by its label,
        // a release to the default channel.
        channels = listOf(version.toString().substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
    }
}

// Runs the plugin - built against the oldest supported platform - inside the newest supported one.
//
// This is how to try the plugin on a newer CLion: do NOT raise platformVersion to do it. Compiling
// against the newest platform both defeats the point of the range (an API added in 2026.x would
// compile silently and then fail on 2025.3) and does not work, because the 2026.2 platform classes
// carry Kotlin 2.4 metadata that the Kotlin version here cannot read. Running the artifact in the
// newer IDE is also the more faithful test: it is exactly what a user on 2026.2 installs.
intellijPlatformTesting {
    runIde {
        register("runIdeNewest") {
            type = IntelliJPlatformType.CLion
            version = providers.gradleProperty("verifyAgainstVersion")
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        options.release.set(JVM_TARGET)
    }

    test {
        useJUnitPlatform()
    }

    // Any task that launches the IDE - runIde, the plugin verifier - runs it out of the distribution
    // Gradle extracted into its transform cache, which Gradle treats as an immutable workspace.
    // CLion's bundled GDB embeds CPython, and on startup it writes __pycache__/*.pyc next to its
    // Python support modules inside that distribution. The added files change the workspace, and the
    // next build fails with "the contents of the immutable workspace have been modified" - which
    // reads like disk corruption but is just bytecode caching. Telling CPython not to write it keeps
    // the distribution byte-identical.
    //
    // If it happens anyway, the repair is to delete the __pycache__ directories under
    // <transform>/transformed/CLion-*/bin/gdb/, not to wipe the multi-gigabyte cache entry.
    withType<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>().configureEach {
        environment("PYTHONDONTWRITEBYTECODE", "1")
    }
    withType<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>().configureEach {
        environment("PYTHONDONTWRITEBYTECODE", "1")
    }
}

kotlin {
    // CLion 2025.3 runs on JBR 21, so compile on 21 and target 21.
    //
    // The toolchain must really be 21, not merely target it: the IntelliJ Platform Gradle Plugin asks
    // Gradle for a 21 toolchain, and pointing Kotlin at a newer JDK instead makes it derive a JVM
    // target this Kotlin version does not know. Current JetBrains IDEs all bundle JBR 25, so a
    // developer machine usually has no 21 lying around - settings.gradle.kts lets Gradle fetch one.
    jvmToolchain(JVM_TARGET)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Internal API is allowed only where verifier-allowed-internal-api.txt says so; see failureLevel.
val checkInternalApiUsages = tasks.register("checkInternalApiUsages") {
    description = "Fails on internal API usages the plugin verifier found that are not explicitly allowed."
    val allowList = layout.projectDirectory.file("verifier-allowed-internal-api.txt")
    val reports = layout.buildDirectory.dir("reports/pluginVerifier")
    inputs.file(allowList)
    doLast {
        val allowed = allowList.asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { Regex(it) }
        val usages = reports.get().asFile.walkTopDown()
            .filter { it.name == "internal-api-usages.txt" }
            .flatMap { it.readLines().asSequence() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val unexpected = usages.filter { usage -> allowed.none { it.containsMatchIn(usage) } }
        // An entry nothing matches any more is an exemption kept for code that is gone.
        val stale = allowed.filter { regex -> usages.none { regex.containsMatchIn(it) } }
        val problems = unexpected.map { "Internal API usage not in verifier-allowed-internal-api.txt: $it" } +
            stale.map { "Allowed internal API no longer used; remove it: ${it.pattern}" }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString(System.lineSeparator()))
        }
    }
}
tasks.named("verifyPlugin") { finalizedBy(checkInternalApiUsages) }
