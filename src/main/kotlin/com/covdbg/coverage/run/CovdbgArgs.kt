package com.covdbg.coverage.run

/**
 * Builds covdbg command lines.
 *
 * Deliberately free of IntelliJ types so the argument shape can be unit tested without an IDE.
 *
 * covdbg 1.3.0 takes no licensing options at all: a run is decided from who is signed in
 * (`covdbg login`) or from COVDBG_PROJECT_TOKEN in the environment. The 1.2-era `--license`,
 * `--license-file`, `--fetch-license` and `--appdata` options were removed, and CLI11 rejects
 * unknown options, so passing any of them fails the run during argument parsing.
 */
object CovdbgArgs {

    /** Everything the default (no subcommand) run mode needs. */
    data class RunSpec(
        /** Path to .covdbg.yaml. Blank means let covdbg discover one from the working directory. */
        val configPath: String? = null,
        /** Absolute output path for the .covdb. Always passed, never left to covdbg's default. */
        val outputPath: String,
        /** ONE_SHOT or PERSISTENT. */
        val mode: String = "ONE_SHOT",
        val logLevel: String? = null,
        val logFile: String? = null,
        /** covdbg or native. Blank means covdbg's own default. */
        val symbolEngine: String? = null,
        val followChildren: Boolean = false,
        val target: String,
        val targetArgs: List<String> = emptyList()
    )

    /**
     * `covdbg [options] <target> [target args...]`
     *
     * The target and its arguments come last. covdbg calls `positionals_at_end()`, so everything
     * after the target belongs to the target and no `--` separator is needed.
     */
    fun run(spec: RunSpec): List<String> = buildList {
        addOption("--config", spec.configPath)
        add("--output")
        add(spec.outputPath)
        if (spec.mode.isNotBlank()) {
            add("--mode")
            add(spec.mode)
        }
        addOption("--log-level", spec.logLevel)
        addOption("--log-file", spec.logFile)
        addOption("--symbol-engine", spec.symbolEngine)
        if (spec.followChildren) {
            add("--follow-children")
        }
        add(spec.target)
        addAll(spec.targetArgs)
    }

    fun login(): List<String> = listOf("login")

    fun logout(): List<String> = listOf("logout")

    fun whoami(): List<String> = listOf("whoami")

    fun version(): List<String> = listOf("--version")

    /**
     * `covdbg convert -i <covdb> -f HTML -o <directory>`
     *
     * For HTML the output is a directory, and covdbg requires it.
     */
    fun convertHtml(
        input: String,
        outputDir: String,
        title: String? = null,
        sourceRoot: String? = null
    ): List<String> = buildList {
        add("convert")
        add("--input")
        add(input)
        add("--format")
        add("HTML")
        addOption("--html-title", title)
        addOption("--html-source-root", sourceRoot)
        add("--output")
        add(outputDir)
    }

    /** Append `name value` when the value carries something; omit the option entirely otherwise. */
    private fun MutableList<String>.addOption(name: String, value: String?) {
        if (!value.isNullOrBlank()) {
            add(name)
            add(value)
        }
    }
}
