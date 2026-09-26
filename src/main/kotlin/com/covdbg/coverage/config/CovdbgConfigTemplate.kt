package com.covdbg.coverage.config

/**
 * The starter `.covdbg.yaml` this plugin scaffolds.
 *
 * The text is kept as a resource rather than a Kotlin string so it stays diffable against the VS
 * Code extension's own starter config, which it is copied from — both IDEs should scaffold the same
 * file. The only addition is a commented-out `baseline:` block, which documents covdbg 1.3.0's
 * answer for static-library code without changing the effective configuration.
 */
object CovdbgConfigTemplate {

    const val FILE_NAME = ".covdbg.yaml"

    private const val RESOURCE = "/config/covdbg-starter.yaml"

    fun starter(): String =
        CovdbgConfigTemplate::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Missing plugin resource $RESOURCE")
}
