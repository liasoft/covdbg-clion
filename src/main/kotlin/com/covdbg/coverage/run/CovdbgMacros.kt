package com.covdbg.coverage.run

import com.intellij.execution.util.ProgramParametersConfigurator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Expands IDE macros such as `$ProjectFileDir$` in the fields of a covdbg run configuration.
 *
 * CLion run configurations need none of this: CLion expands them itself before
 * [CovdbgRunConfigurationExtension] sees the command line. This covers the covdbg run configuration,
 * whose fields are expanded one by one with the platform call CLion uses.
 */
object CovdbgMacros {

    private val LOG = Logger.getInstance(CovdbgMacros::class.java)

    /**
     * [text] with its macros expanded, or [text] unchanged when a macro cannot be expanded.
     *
     * [validation] is for `checkConfiguration`, which runs without a launch context: macros that
     * need one (`$FilePath$`) are then left as written instead of failing.
     */
    fun expand(text: String, project: Project, validation: Boolean = false): String {
        if (text.isBlank() || !text.contains('$')) return text
        return try {
            ProgramParametersConfigurator()
                .apply { setValidation(validation) }
                .expandPathAndMacros(text, null, project)
                ?: text
        } catch (e: ProgramParametersConfigurator.ParametersConfiguratorException) {
            unexpanded(text, e, validation)
        } catch (e: RuntimeException) {
            unexpanded(text, e, validation)
        }
    }

    private fun unexpanded(text: String, e: Exception, validation: Boolean): String {
        if (!validation) {
            LOG.warn("Could not expand macros in '$text'; using it as written: ${e.message}")
        }
        return text
    }
}
