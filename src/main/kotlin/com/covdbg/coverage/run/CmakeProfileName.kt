package com.covdbg.coverage.run

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.diagnostic.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * The CMake profile a run configuration would run with, for hiding "Run with covdbg" on toolchains
 * covdbg cannot instrument.
 *
 * Only `canRun` needs this: it has neither an execution environment nor a toolchain to ask. Once a
 * run starts, [CovdbgRunConfigurationExtension] gets the real `CPPEnvironment` and checks `isMSVC()`.
 *
 * Reflective, because the members live on CLion's CMake run-configuration classes, which cannot be
 * named from a third-party plugin across the supported build range: in 2025.3 they live in the CMake
 * plugin's main jar, but in 2026.2 they moved into the content module `intellij.cmake.core`, which is
 * declared `visibility="internal"` and is not on the plugin's classpath. Reflecting on the *instance*
 * sidesteps that - the class is never named, and the members used below have identical signatures in
 * both versions.
 */
object CmakeProfileName {

    private val LOG = Logger.getInstance(CmakeProfileName::class.java)

    /** The profile name, or null when the configuration is not a CMake one or it cannot be read. */
    fun of(configuration: RunProfile, target: ExecutionTarget): String? {
        // CMakeAppRunConfiguration.getBuildAndRunConfigurations(ExecutionTarget): the object CLion
        // itself uses to answer "what would running this launch?", so it respects the selected profile.
        val buildAndRun = invoke(
            configuration,
            "getBuildAndRunConfigurations",
            ExecutionTarget::class.java,
            target
        ) ?: return null
        // BuildAndRunConfigurations.buildConfiguration.getProfileName()
        val buildConfiguration = readField(buildAndRun, "buildConfiguration") ?: return null
        return invoke(buildConfiguration, "getProfileName") as? String
    }

    // canRun() runs on every action-update pass, and javaClass.methods/fields clone their arrays
    // on each call, so both lookups are cached per class.
    private val methods = ConcurrentHashMap<String, Optional<Method>>()

    private class Optional<T>(val value: T?)

    private fun invoke(
        target: Any,
        name: String,
        parameterType: Class<*>? = null,
        argument: Any? = null
    ): Any? {
        val key = "${target.javaClass.name}#$name/${parameterType?.name ?: ""}"
        val method = methods.getOrPut(key) {
            Optional(
                target.javaClass.methods.firstOrNull {
                    it.name == name &&
                        it.parameterCount == (if (parameterType == null) 0 else 1) &&
                        (parameterType == null || it.parameterTypes[0].isAssignableFrom(parameterType))
                }
            )
        }.value ?: return null

        return try {
            if (parameterType == null) method.invoke(target) else method.invoke(target, argument)
        } catch (e: Exception) {
            LOG.debug("$name on ${target.javaClass.name} failed: ${e.cause?.message ?: e.message}")
            null
        }
    }

    private val fields = ConcurrentHashMap<String, Optional<Field>>()

    private fun readField(target: Any, name: String): Any? =
        try {
            fields.getOrPut("${target.javaClass.name}#$name") {
                Optional(target.javaClass.fields.firstOrNull { it.name == name })
            }.value?.get(target)
        } catch (e: Exception) {
            LOG.debug("Field $name on ${target.javaClass.name} failed: ${e.message}")
            null
        }
}
