package com.covdbg.coverage.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "covdbg Settings", storages = [Storage("covdbg.xml")])
class CovdbgSettings : PersistentStateComponent<CovdbgSettings.State> {

    /**
     * covdbg 1.3.0 takes no licensing options: a run is decided from who is signed in
     * (`covdbg login`) or from COVDBG_PROJECT_TOKEN in the environment. The license file, license
     * token and fetch-license settings earlier versions stored are gone, along with the unused
     * output-format setting.
     *
     * Settings files written by those versions still load: the serializer ignores stored entries
     * with no matching field. The stale entries linger in covdbg.xml until settings are next
     * applied, which is harmless.
     */
    data class State(
        var covdbgPath: String = "",            // Path to covdbg.exe
        var configPath: String = "",            // Path to .covdbg.yaml (empty = auto-discover)
        var logLevel: String = "WARN",
        var logFile: String = "",               // Empty = covdbg default (.covdbg/Logs/covdbg.log)
        var symbolEngine: String = "",          // Empty = covdbg default; otherwise covdbg or native
        var followChildren: Boolean = false     // When running CLion configurations with covdbg
    )

    private var _state = State()

    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    companion object {
        fun getInstance(project: Project): CovdbgSettings =
            project.getService(CovdbgSettings::class.java)
    }
}
