package com.covdbg.coverage.run

import com.intellij.execution.configurations.GeneralCommandLine

/**
 * Puts covdbg in front of a target's command line: `app.exe a b` becomes
 * `covdbg.exe [options] app.exe a b`.
 *
 * Only the executable and the parameters change. The working directory, environment, input
 * redirection and terminal the command line already carries stay as the IDE set them, and reach the
 * target through covdbg, which starts it with its own environment, working directory and stdin.
 */
object CovdbgCommandLinePatch {

    /**
     * @param covdbgArguments Builds covdbg's arguments from the target's own, which must end the list
     *   (see [CovdbgArgs.run]).
     */
    fun apply(
        commandLine: GeneralCommandLine,
        covdbgExecutable: String,
        covdbgArguments: (targetArgs: List<String>) -> List<String>
    ) {
        val targetArgs = commandLine.parametersList.list
        val parameters = covdbgArguments(targetArgs)
        commandLine.exePath = covdbgExecutable
        commandLine.parametersList.clearAll()
        commandLine.parametersList.addAll(parameters)
    }
}
