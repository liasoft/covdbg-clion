package com.covdbg.coverage.settings

import com.covdbg.coverage.CovdbgIcons
import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.run.CovdbgExecutableResolver
import com.covdbg.coverage.run.CovdbgVersion
import com.covdbg.coverage.run.CovdbgRuntimeInfoService
import com.covdbg.coverage.seats.CovdbgLoginTask
import com.covdbg.coverage.seats.CovdbgLogoutTask
import com.covdbg.coverage.seats.CovdbgSeatListener
import com.covdbg.coverage.seats.CovdbgSeatService
import com.covdbg.coverage.seats.SignInStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.covdbg.coverage.ui.chooseFile
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.Alarm
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.event.DocumentEvent
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class CovdbgSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    private val covdbgPathField = TextFieldWithBrowseButton()
    private val configPathField = TextFieldWithBrowseButton()
    private val logLevelCombo = ComboBox(arrayOf("ERROR", "WARN", "INFO", "DEBUG", "TRACE"))
    private val logFileField = TextFieldWithBrowseButton()

    // Empty means "leave it to covdbg", which is the right default for an advanced knob.
    private val symbolEngineCombo = ComboBox(arrayOf("", "covdbg", "native"))
    private val followChildrenCheckbox = JBCheckBox("Follow child processes when running CLion configurations")

    private val versionLabel = JBLabel(" ")
    private val signInLabel = JBLabel(" ")
    private val signInButton = JButton("Sign In…")
    private val signOutButton = JButton("Sign Out")
    private val refreshButton = JButton("Refresh")

    private var panel: JPanel? = null

    /**
     * Owns the sign-in subscription. The settings dialog can be opened many times per session, so
     * tying the connection to the project instead would accumulate one listener per visit.
     */
    private var uiDisposable: Disposable? = null

    private lateinit var probeAlarm: Alarm

    override fun getId(): String = CovdbgNotifications.SETTINGS_ID

    override fun getDisplayName(): String = "Covdbg"

    /** The covdbg logo and name, so the page is recognisable at a glance. */
    private fun header(): JComponent =
        JBLabel("covdbg", CovdbgIcons.LogoLarge, SwingConstants.LEADING).apply {
            font = JBFont.h3().asBold()
            iconTextGap = JBUI.scale(8)
        }

    override fun createComponent(): JComponent {
        covdbgPathField.chooseFile(
            project,
            "Select Covdbg Executable",
            "Optional. Leave empty to use covdbg from PATH."
        )
        @Suppress("DialogTitleCapitalization")
        configPathField.chooseFile(
            project,
            "Select .covdbg.yaml File",
            "Choose a covdbg configuration file (optional)."
        )
        logFileField.chooseFile(
            project,
            "Select Log File",
            "Leave empty to use covdbg's default (.covdbg/Logs/covdbg.log)."
        )

        covdbgPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
            // Debounced: every keystroke would otherwise queue a resolve, and spawn covdbg --version
            // once the path happens to be valid.
            override fun textChanged(e: DocumentEvent) {
                probeAlarm.cancelAllRequests()
                probeAlarm.addRequest(::refreshRuntimeInfo, PROBE_DELAY_MS)
            }
        })

        signInButton.addActionListener { CovdbgLoginTask(project).queue() }
        signOutButton.addActionListener { CovdbgLogoutTask(project).queue() }
        refreshButton.addActionListener { CovdbgSeatService.getInstance(project).refresh() }

        val signInButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(signInButton)
            add(signOutButton)
            add(refreshButton)
        }

        val disposable = Disposer.newDisposable("covdbg settings panel")
        uiDisposable = disposable
        probeAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
        project.messageBus.connect(disposable).subscribe(
            CovdbgSeatListener.TOPIC,
            object : CovdbgSeatListener {
                override fun signInStatusChanged(status: SignInStatus) = showSignInStatus(status)
            }
        )

        val form = FormBuilder.createFormBuilder()
            .addComponent(header())
            .addSeparator()
            .addLabeledComponent("Covdbg path:", covdbgPathField)
            .addComponentToRightColumn(
                JBLabel("Leave empty to use covdbg from PATH or a known install location.").also {
                    it.foreground = UIUtil.getContextHelpForeground()
                }
            )
            .addComponentToRightColumn(versionLabel)
            .addLabeledComponent("Signed in as:", signInLabel)
            .addComponentToRightColumn(signInButtons)
            .addLabeledComponent("Default config path:", configPathField)
            .addLabeledComponent("Log level:", logLevelCombo)
            .addLabeledComponent("Log file:", logFileField)
            .addLabeledComponent("Symbol engine:", symbolEngineCombo)
            .addComponent(followChildrenCheckbox)
            .panel

        panel = JPanel(BorderLayout()).apply {
            add(form, BorderLayout.NORTH)
        }

        showSignInStatus(CovdbgSeatService.getInstance(project).status())
        CovdbgSeatService.getInstance(project).refreshIfStale()

        return panel as JPanel
    }

    override fun isModified(): Boolean {
        val state = CovdbgSettings.getInstance(project).state
        return covdbgPathField.text.trim() != state.covdbgPath ||
            configPathField.text.trim() != state.configPath ||
            logLevelCombo.selectedItem?.toString() != state.logLevel ||
            logFileField.text.trim() != state.logFile ||
            (symbolEngineCombo.selectedItem?.toString() ?: "") != state.symbolEngine ||
            followChildrenCheckbox.isSelected != state.followChildren
    }

    override fun apply() {
        val state = CovdbgSettings.getInstance(project).state
        val oldExe = state.covdbgPath
        state.covdbgPath = covdbgPathField.text.trim()
        state.configPath = configPathField.text.trim()
        state.logLevel = logLevelCombo.selectedItem?.toString() ?: "WARN"
        state.logFile = logFileField.text.trim()
        state.symbolEngine = symbolEngineCombo.selectedItem?.toString() ?: ""
        state.followChildren = followChildrenCheckbox.isSelected

        if (oldExe != state.covdbgPath) {
            // A different covdbg may be signed in as someone else, or not installed at all.
            CovdbgExecutableResolver.invalidate()
            CovdbgSeatService.getInstance(project).refresh()
        }
    }

    override fun reset() {
        val state = CovdbgSettings.getInstance(project).state
        covdbgPathField.text = state.covdbgPath
        configPathField.text = state.configPath
        logLevelCombo.selectedItem = state.logLevel
        logFileField.text = state.logFile
        symbolEngineCombo.selectedItem = state.symbolEngine
        followChildrenCheckbox.isSelected = state.followChildren

        // Setting the path field fires no change when the text stays the same - always the case for
        // the default, empty path - so the version would never be looked up. Do it directly, in place
        // of the debounced request any change did queue.
        if (!::probeAlarm.isInitialized) return
        probeAlarm.cancelAllRequests()
        refreshRuntimeInfo()
    }

    override fun disposeUIResources() {
        uiDisposable?.let { Disposer.dispose(it) }
        uiDisposable = null
        panel = null
    }

    private fun showSignInStatus(status: SignInStatus) {
        signInLabel.text = status.label
        signInLabel.toolTipText = status.tooltip
        // Signing out is pointless with no session, and harmless when we simply do not know.
        signOutButton.isEnabled = status !is SignInStatus.NotSignedIn &&
            status !is SignInStatus.ProjectToken
    }

    /**
     * Resolves covdbg and probes its version off the EDT.
     *
     * This reflects the field as typed rather than as saved, so an empty field immediately shows
     * whatever will actually be used - which is the point of making the setting optional.
     */
    private fun refreshRuntimeInfo() {
        val configured = covdbgPathField.text.trim()
        versionLabel.text = "Checking…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val resolved = CovdbgExecutableResolver.resolve(configured)
            val version = resolved?.let { CovdbgRuntimeInfoService.getInstance().version(it.path) }
            ApplicationManager.getApplication().invokeLater({
                if (panel == null || covdbgPathField.text.trim() != configured) return@invokeLater
                versionLabel.text = when {
                    resolved == null -> "covdbg was not found on PATH or at a known install location"
                    version == null -> "${resolved.describe()} — not runnable, or it reported no version"
                    version < CovdbgVersion.MINIMUM_SUPPORTED ->
                        "covdbg $version at ${resolved.describe()} — ${CovdbgVersion.REQUIREMENT}"
                    else -> "covdbg $version at ${resolved.describe()}"
                }
                // The Settings dialog is modal: in the default, non-modal state this would be queued
                // behind it and run only once it closes. Any modality is safe for a label update that
                // does nothing once the page is gone - and the page may not be inside the dialog yet
                // when reset() asks, so its component's modality cannot be relied on.
            }, ModalityState.any())
        }
    }

    private companion object {
        const val PROBE_DELAY_MS = 300
    }

}
