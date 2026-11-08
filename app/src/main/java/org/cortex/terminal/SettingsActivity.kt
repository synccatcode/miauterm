package org.cortex.terminal

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.cortex.terminal.runtime.DebianBackup
import org.cortex.terminal.runtime.DebianOperationRunner
import org.cortex.terminal.runtime.DebianRuntime
import org.cortex.terminal.runtime.Environment
import org.cortex.terminal.service.CortexService
import org.cortex.terminal.view.ExtraKeysConfig
import org.cortex.terminal.view.ExtraKeysLayout
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private var operationDialog: AlertDialog? = null

    private val operationListener: (DebianOperationRunner.State) -> Unit = { state ->
        runOnUiThread {
            if (!isFinishing && !isDestroyed) handleOperationState(state)
        }
    }

    internal val backupPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            runDebianOperation(getString(R.string.debian_backup_done), closeSettings = false) { context ->
                DebianBackup.export(context, it)
            }
        }
    }
    internal val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { confirmRestore(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val container = findViewById<android.view.View>(R.id.settings_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        val themeKey = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getString("terminal_theme", "catppuccin") ?: "catppuccin"
        org.cortex.terminal.emulator.TerminalColor.applyTheme(themeKey)
        org.cortex.terminal.view.Chrome.apply(
            org.cortex.terminal.emulator.TerminalColor.SCHEMES.getValue(org.cortex.terminal.emulator.TerminalColor.currentThemeId)
        )
        val p = org.cortex.terminal.view.Chrome.palette
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(p.bg))
        findViewById<android.view.View>(R.id.settings_root).setBackgroundColor(p.bg)
        supportActionBar?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(p.bar))
        supportActionBar?.title = android.text.SpannableString(getString(R.string.settings_title)).apply {
            setSpan(android.text.style.ForegroundColorSpan(p.text), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back)?.let {
            supportActionBar?.setHomeAsUpIndicator(it.mutate().apply { setTint(p.text) })
        }
        window.statusBarColor = p.bar
        window.navigationBarColor = p.bar
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !p.dark
        controller.isAppearanceLightNavigationBars = !p.dark

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    internal fun showProotLog() {
        val log = DebianRuntime.prootLog(this)
        val text = if (log.isFile) runCatching { log.readText().takeLast(32 * 1024) }.getOrDefault("") else ""
        val body = android.widget.TextView(this).apply {
            setTextColor(org.cortex.terminal.view.Chrome.palette.text)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(18, 14, 18, 14)
            this.text = text.ifBlank { getString(R.string.proot_log_empty) }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(body) }
        org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(R.string.pref_view_proot_log_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        ).show()
    }

    internal fun confirmReset() {
        org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.debian_reset_confirm_title, DebianRuntime.systemName(this) ?: "Linux system"))
                .setMessage(R.string.debian_reset_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.pref_reset_debian_title) { _, _ ->
                    runDebianOperation(getString(R.string.debian_reset_done), closeSettings = true) { context ->
                        stopSessionsAndForget(context)
                        check(DebianRuntime.install(context, force = true)) {
                            context.getString(R.string.debian_install_failed)
                        }
                    }
                }
                .create()
        ).show()
    }

    private fun confirmRestore(uri: Uri) {
        org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(R.string.debian_restore_confirm_title)
                .setMessage(R.string.debian_restore_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.pref_restore_debian_title) { _, _ ->
                    runDebianOperation(getString(R.string.debian_restore_done), closeSettings = true) { context ->
                        stopSessionsAndForget(context)
                        DebianBackup.import(context, uri)
                    }
                }
                .create()
        ).show()
    }

    private fun stopSessionsAndForget(context: android.content.Context) {
        CortexService.getOrCreateSessionManager(context)
            .destroyAll(forget = true, waitForProcesses = true, notify = false)
    }

    private fun runDebianOperation(
        successMessage: String,
        closeSettings: Boolean,
        operation: (android.content.Context) -> Unit
    ) {
        if (!DebianOperationRunner.start(this, successMessage, closeSettings, operation)) {
            android.widget.Toast.makeText(this, R.string.debian_operation_running, android.widget.Toast.LENGTH_SHORT).show()
            showOperationDialog()
        }
    }

    private fun handleOperationState(state: DebianOperationRunner.State) {
        when (state) {
            DebianOperationRunner.State.Running -> showOperationDialog()
            is DebianOperationRunner.State.Finished -> {
                operationDialog?.dismiss()
                operationDialog = null
                if (state.success) {
                    android.widget.Toast.makeText(this, state.message.orEmpty(), android.widget.Toast.LENGTH_LONG).show()
                    if (state.closeSettings) finish()
                    else if (DebianRuntime.isRemoved(this)) recreate()
                } else {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.debian_operation_failed, state.message ?: getString(R.string.operation_failed_unknown)),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            DebianOperationRunner.State.Idle -> {
                operationDialog?.dismiss()
                operationDialog = null
            }
        }
    }

    private fun showOperationDialog() {
        if (operationDialog?.isShowing == true) return
        operationDialog = org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(R.string.debian_operation_running)
                .setView(android.widget.ProgressBar(this).apply { isIndeterminate = true })
                .setCancelable(false)
                .create()
        ).also { it.show() }
    }

    internal fun editExtraKeys() {
        val file = File(Environment.getHomeDir(this), ".config/miauterm/${ExtraKeysConfig.FILE_NAME}")
        val initial = runCatching { if (file.isFile) file.readText() else ExtraKeysLayout.DEFAULT_TEXT + "\n" }
            .getOrDefault(ExtraKeysLayout.DEFAULT_TEXT + "\n")
        val editor = android.widget.EditText(this).apply {
            setText(initial)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            minLines = 8
            maxLines = 14
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(18, 12, 18, 12)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(8, 0, 8, 0)
            addView(editor, android.widget.LinearLayout.LayoutParams(-1, (230 * resources.displayMetrics.density).toInt()))
            addView(android.widget.Button(this@SettingsActivity).apply {
                text = getString(R.string.extra_keys_add_key)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    val keys = ExtraKeysLayout.Special.values()
                    val textKeys = ('a'..'z').map { it.toString() } +
                        ('A'..'Z').map { it.toString() } +
                        ('0'..'9').map { it.toString() } +
                        listOf(
                            "-", "_", "=", "+", "[", "]", "{", "}", "(", ")", "<", ">", "/", "?", "\\",
                            "|", ":", ";", "'", "\"", ",", ".", "~", "!", "@", "#", "\$", "%", "^", "&", "*"
                        )
                    val options = keys.map { key ->
                        if (key.name == key.label) key.name else "${key.name} (${key.label})"
                    } + textKeys + getString(R.string.extra_keys_custom)
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(R.string.extra_keys_add_key)
                        .setItems(options.toTypedArray()) { _, which ->
                            when {
                                which < keys.size -> appendExtraKey(editor, file, keys[which].name, textKey = false)
                                which < keys.size + textKeys.size -> {
                                    appendExtraKey(editor, file, textKeys[which - keys.size], textKey = true)
                                }
                                which == options.lastIndex -> {
                                    val input = android.widget.EditText(this@SettingsActivity).apply {
                                        setSingleLine(true)
                                        hint = getString(R.string.extra_keys_custom)
                                    }
                                    AlertDialog.Builder(this@SettingsActivity)
                                        .setTitle(R.string.extra_keys_custom)
                                        .setView(input)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            val value = input.text.toString()
                                            if (value.isNotBlank()) appendExtraKey(editor, file, value, textKey = true)
                                        }
                                        .show().also { org.cortex.terminal.view.ChromeDialog.style(it) }
                                }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show().also { org.cortex.terminal.view.ChromeDialog.style(it) }
                }
            }, android.widget.LinearLayout.LayoutParams(-1, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pref_extra_keys_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.extra_keys_reset) { _, _ ->
                file.delete()
                MainActivity.instance?.reloadExtraKeys()
                android.widget.Toast.makeText(this, R.string.extra_keys_saved, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.save, null)
            .create()
        org.cortex.terminal.view.ChromeDialog.style(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = editor.text.toString()
                val parsed = ExtraKeysLayout.parse(text)
                when {
                    text.toByteArray(Charsets.UTF_8).size > 64 * 1024 -> editor.error = getString(R.string.extra_keys_invalid, "over 64 KB")
                    !ExtraKeysLayout.isNestedArrayFormat(text) -> editor.error = getString(R.string.extra_keys_array_format)
                    parsed.error != null -> editor.error = getString(R.string.extra_keys_invalid, parsed.error)
                    else -> runCatching { ExtraKeysConfig.writeLayout(file, text) }.onSuccess {
                        MainActivity.instance?.reloadExtraKeys()
                        dialog.dismiss()
                        android.widget.Toast.makeText(this, R.string.extra_keys_saved, android.widget.Toast.LENGTH_SHORT).show()
                    }.onFailure { editor.error = it.message }
                }
            }
        }.show()
    }

    private fun appendExtraKey(
        editor: android.widget.EditText,
        file: File,
        value: String,
        textKey: Boolean
    ) {
        runCatching {
            ExtraKeysLayout.appendKey(editor.text.toString(), value, textKey).also {
                ExtraKeysConfig.writeLayout(file, it)
            }
        }
            .onSuccess { updated ->
                editor.setText(updated)
                editor.setSelection(editor.text.length)
                editor.error = null
                MainActivity.instance?.reloadExtraKeys()
                android.widget.Toast.makeText(this, R.string.extra_keys_saved, android.widget.Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                editor.error = getString(R.string.extra_keys_invalid, error.message ?: "invalid layout")
            }
    }

    internal fun showAbout() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val palette = org.cortex.terminal.view.Chrome.palette
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
            val iconSize = (72 * resources.displayMetrics.density).toInt()
            addView(android.widget.ImageView(this@SettingsActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = getString(R.string.app_name)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }, android.widget.LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            })
            addView(android.widget.TextView(this@SettingsActivity).apply {
                text = getString(R.string.about_description) + "\n" + getString(R.string.about_version, version)
                setTextColor(palette.text)
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 14, 0, 14)
            })
            addView(android.widget.TextView(this@SettingsActivity).apply {
                text = getString(R.string.about_fork)
                setTextColor(palette.textDim)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
            })
            addView(android.widget.TextView(this@SettingsActivity).apply {
                text = getString(R.string.about_open_source_notices)
                setTextColor(palette.accent)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 18, 0, 8)
                isClickable = true
                setOnClickListener { showOpenSourceNotices() }
            })
        }
        org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(R.string.pref_about_title)
                .setView(content)
                .setPositiveButton(R.string.ok, null)
                .create()
        ).show()
    }

    private fun showOpenSourceNotices() {
        val notices = runCatching {
            assets.open("licenses/THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        }.getOrDefault(getString(R.string.about_notices_unavailable))
        val body = android.widget.TextView(this).apply {
            text = notices
            setTextColor(org.cortex.terminal.view.Chrome.palette.text)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(20, 16, 20, 16)
        }
        org.cortex.terminal.view.ChromeDialog.style(
            AlertDialog.Builder(this)
                .setTitle(R.string.about_open_source_notices)
                .setView(android.widget.ScrollView(this).apply { addView(body) })
                .setPositiveButton(R.string.ok, null)
                .create()
        ).show()
    }

    override fun onStart() {
        super.onStart()
        DebianOperationRunner.addListener(operationListener)
    }

    override fun onStop() {
        DebianOperationRunner.removeListener(operationListener)
        super.onStop()
    }

    override fun onDestroy() {
        operationDialog?.dismiss()
        operationDialog = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("secure_screen", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private var enableSharingWhenReturningFromSystemSettings = false

        private val sharedFolderPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val granted = hasSharedFolderPermission()
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, granted).apply()
            updateSharedFolderPreference()
            android.widget.Toast.makeText(
                requireContext(),
                if (granted) R.string.shared_folder_access_granted_toast else R.string.shared_folder_access_denied_toast,
                android.widget.Toast.LENGTH_LONG
            ).show()
            if (!granted) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.shared_folder_permission_denied_title)
                    .setMessage(R.string.shared_folder_permission_denied_message)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        openStoragePermissionSettings(enableSharingOnReturn = true)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        override fun onResume() {
            super.onResume()
            if (enableSharingWhenReturningFromSystemSettings && hasSharedFolderPermission()) {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, true).apply()
            }
            enableSharingWhenReturningFromSystemSettings = false
            updateSharedFolderPreference()
        }

        private fun hasSharedFolderPermission(): Boolean =
            DebianRuntime.hasSharedFolderPermission(requireContext())

        private fun updateSharedFolderPreference() {
            val preference = findPreference<SwitchPreferenceCompat>("shared_folder_access") ?: return
            val context = requireContext()
            val allowed = DebianRuntime.isSharedFolderAccessEnabled(context)
            preference.isChecked = allowed
            preference.summary = getString(
                if (allowed) R.string.shared_folder_access_enabled else R.string.shared_folder_access_disabled
            )
        }

        private fun openStoragePermissionSettings(enableSharingOnReturn: Boolean = false) {
            val context = requireContext()
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            runCatching {
                startActivity(intent)
                enableSharingWhenReturningFromSystemSettings = enableSharingOnReturn
            }.onFailure {
                enableSharingWhenReturningFromSystemSettings = false
                android.widget.Toast.makeText(context, R.string.storage_permission_open_failed, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            SettingsStyle(this).install()
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is ListPreference) {

                if (preference.key == "terminal_theme") ThemePicker.show(requireContext(), preference)
                else showChoice(preference)
                return
            }
            super.onDisplayPreferenceDialog(preference)
        }

        private fun showChoice(pref: ListPreference) {
            val context = requireContext()
            val entries = pref.entries ?: return
            val values = pref.entryValues ?: return
            val p = org.cortex.terminal.view.Chrome.palette
            val checked = values.indexOfFirst { it.toString() == pref.value }
            val adapter = object : android.widget.ArrayAdapter<CharSequence>(
                context, android.R.layout.simple_list_item_single_choice, entries
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val row = super.getView(position, convertView, parent) as android.widget.CheckedTextView
                    row.setTextColor(p.text)
                    row.checkMarkTintList = android.content.res.ColorStateList.valueOf(p.accent)
                    return row
                }
            }
            val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(pref.dialogTitle ?: pref.title)
                .setSingleChoiceItems(adapter, checked) { d, which ->
                    val value = values[which].toString()
                    if (pref.callChangeListener(value)) pref.value = value
                    d.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            org.cortex.terminal.view.ChromeDialog.style(dialog).show()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            updateSharedFolderPreference()
            findPreference<SwitchPreferenceCompat>("shared_folder_access")?.apply {
                isPersistent = false
                setOnPreferenceChangeListener { _, newValue ->
                    val requested = newValue as Boolean
                    val currentlyAllowed = hasSharedFolderPermission()
                    if (requested && !currentlyAllowed) {
                        sharedFolderPermissionRequest.launch(
                            arrayOf(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    } else {
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit().putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, requested).apply()
                        updateSharedFolderPreference()
                        if (!requested && currentlyAllowed) {
                            openStoragePermissionSettings()
                        }
                    }
                    false
                }
            }

            findPreference<ListPreference>(LocaleHelper.PREF_KEY)?.let { pref ->

                val available = resources.getStringArray(R.array.language_values).toList()
                pref.value = LocaleHelper.currentSelection(available)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    LocaleHelper.setLanguage(newValue as String)
                    true
                }
            }

            findPreference<ListPreference>("terminal_font_size")?.summaryProvider =
                Preference.SummaryProvider<ListPreference> { pref -> pref.entry ?: pref.value?.let { "$it sp" } }

            findPreference<androidx.preference.SwitchPreferenceCompat>(DebianRuntime.PREF_PROOT_DIAGNOSTICS)
                ?.summary = getString(R.string.pref_proot_diagnostics_summary)

            findPreference<Preference>("view_proot_log")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).showProotLog()
                true
            }
            findPreference<Preference>("clear_proot_log")?.setOnPreferenceClickListener {
                val ok = DebianRuntime.clearProotLog(requireContext())
                android.widget.Toast.makeText(
                    requireContext(),
                    if (ok) R.string.proot_log_cleared else R.string.proot_log_clear_failed,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                true
            }
            findPreference<Preference>("failsafe_session")?.setOnPreferenceClickListener {
                MainActivity.pendingFailsafeSession = true
                activity?.finish()
                true
            }
            findPreference<Preference>("backup_debian")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).backupPicker.launch(getString(R.string.debian_backup_filename))
                true
            }
            findPreference<Preference>("restore_debian")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).restorePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                true
            }
            findPreference<Preference>("reset_debian")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).confirmReset()
                true
            }
            findPreference<androidx.preference.EditTextPreference>(DebianRuntime.PREF_LINUX_USER)?.let { pref ->
                pref.setOnPreferenceChangeListener { _, newValue ->
                    val requested = (newValue as? String).orEmpty().trim()
                    val accounts = DebianRuntime.accounts(Environment.getCortexRoot(requireContext()))
                    if (requested.isEmpty() || accounts.any { it.name == requested }) {
                        true
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.linux_user_not_found),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        false
                    }
                }
            }
            val rootfsPresent = DebianRuntime.isInstalled(requireContext())
            findPreference<Preference>("backup_debian")?.isVisible = rootfsPresent
            findPreference<Preference>("restore_debian")?.isVisible = rootfsPresent
            val systemName = DebianRuntime.systemName(requireContext()) ?: "Linux system"
            findPreference<androidx.preference.PreferenceCategory>("linux_system_category")?.title = systemName
            findPreference<Preference>("reset_debian")?.apply {
                isVisible = rootfsPresent
                title = getString(R.string.rootfs_reset_title, systemName)
            }
            findPreference<Preference>("edit_extra_keys")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).editExtraKeys()
                true
            }
            findPreference<Preference>("about_miauterm")?.setOnPreferenceClickListener {
                (requireActivity() as SettingsActivity).showAbout()
                true
            }

            findPreference<Preference>("bug_report")?.setOnPreferenceClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/synccatcode/miauterm/issues/new/choose")))
                }.onFailure {
                    android.widget.Toast.makeText(requireContext(), R.string.update_connect_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                true
            }
        }
    }
}
