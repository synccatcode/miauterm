package org.cortex.terminal

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.cortex.terminal.databinding.ActivityMainBinding
import org.cortex.terminal.runtime.DebianRuntime
import org.cortex.terminal.runtime.Environment
import org.cortex.terminal.runtime.MiautermCommandInstaller
import org.cortex.terminal.service.CortexService
import org.cortex.terminal.session.SessionAdapter
import org.cortex.terminal.session.SessionManager
import org.cortex.terminal.session.TerminalSession
import org.cortex.terminal.view.Chrome
import org.cortex.terminal.view.ExtraKeysConfig
import org.cortex.terminal.view.ExtraKeysLayout
import org.cortex.terminal.view.ExtraKeysView
import org.cortex.terminal.view.SessionTabsView
import org.cortex.terminal.view.TerminalSearchBar
import org.cortex.terminal.view.TerminalView
import org.cortex.terminal.view.showInChrome

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var instance: MainActivity? = null
            private set

        @Volatile
        var pendingSessionCommand: String? = null
        @Volatile
        var pendingFailsafeSession = false

        private val bootstrapLock = Any()
        private var bootstrapInProgress = false

        @Volatile
        private var startupCleanupDone = false
        private val maintenanceStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private lateinit var binding: ActivityMainBinding

    private val drawerLayout: DrawerLayout get() = binding.drawerLayout
    private val terminalView: TerminalView get() = binding.terminalView
    private val extraKeysView: ExtraKeysView get() = binding.extraKeysView
    private val searchBar: TerminalSearchBar get() = binding.searchBar
    private val btnDrawerSettings: ImageView get() = binding.btnDrawerSettings
    private val btnDrawerFiles: ImageView get() = binding.btnDrawerFiles

    private val sessionRecyclerView: RecyclerView get() = binding.sessionRecyclerView
    private lateinit var sessionAdapter: SessionAdapter
    private val btnDrawerKeyboard: TextView get() = binding.btnDrawerKeyboard
    private val btnDrawerNewSession: TextView get() = binding.btnDrawerNewSession

    private lateinit var sessionManager: SessionManager
    private lateinit var extraKeysConfig: ExtraKeysConfig
    private val applyExtraKeysRunnable = Runnable { applyExtraKeys() }
    private var isBootstrapping = false
    private var runtimeInitializationStarted = false
    private var waitingForNotificationPrompt = false
    private var notificationPromptInterruptedActivity = false
    private var bootstrapDialog: BootstrapDialog? = null
    private var removedRootfsNoticeShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        extraKeysConfig = ExtraKeysConfig(Environment.getHomeDir(this))

        sessionManager = CortexService.getOrCreateSessionManager(this)

        setupBackPressedDispatcher()
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        try {
            val mLeftDraggerField = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
            mLeftDraggerField.isAccessible = true
            val leftDragger = mLeftDraggerField.get(drawerLayout)
            val edgeSizeField = leftDragger.javaClass.getDeclaredField("mEdgeSize")
            edgeSizeField.isAccessible = true
            val density = resources.displayMetrics.density
            edgeSizeField.setInt(leftDragger, (40 * density).toInt())
        } catch (e: Exception) {}

        terminalView.onTerminalTouchWhenDrawerOpen = {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            } else {
                false
            }
        }

        terminalView.onTextSizeChangedByPinch = { sp ->
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("terminal_font_size", sp.toInt().toString())
                .apply()
        }

        extraKeysView.terminalView = terminalView
        extraKeysView.onMenuClick = {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        extraKeysView.onSearchToggle = { showTerminalSearch(animated = true) }
        searchBar.onSwipeRight = { hideTerminalSearch(animated = true) }
        searchBar.onRequestClose = { hideTerminalSearch(animated = true) }
        searchBar.onQueryChanged = { query -> terminalView.startSearch(query) }
        searchBar.onNext = { terminalView.nextSearchHit() }
        searchBar.onPrev = { terminalView.prevSearchHit() }
        searchBar.onClose = { terminalView.clearSearch() }
        terminalView.onSearchChanged = { _, index, total -> searchBar.updateCount(index, total) }

        terminalView.onUrlTapped = { url ->
            try {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                if (clipboard != null) {
                    val clip = android.content.ClipData.newPlainText("URL", url)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.copied_url, url), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
            }
        }

        btnDrawerFiles.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, DebianFilesActivity::class.java))
        }

        btnDrawerSettings.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        instance = this
        requestNotificationsThenInitialize()
    }

    private fun requestNotificationsThenInitialize() {
        if (runtimeInitializationStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            if (manager.getNotificationChannel(CortexService.CHANNEL_ID) == null) {
                waitingForNotificationPrompt = true
                manager.createNotificationChannel(
                    android.app.NotificationChannel(
                        CortexService.CHANNEL_ID,
                        getString(R.string.notif_channel_name),
                        android.app.NotificationManager.IMPORTANCE_LOW
                    ).apply { description = getString(R.string.notif_channel_description) }
                )

                return
            }
        }
        initializeRuntime()
    }

    private fun initializeRuntime() {
        if (runtimeInitializationStarted || isFinishing || isDestroyed) return
        runtimeInitializationStarted = true

        try {
            CortexService.start(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "CortexService.start failed", e)
        }
        sessionManager = CortexService.getOrCreateSessionManager(this)

        sessionAdapter = SessionAdapter(
            sessionManager = sessionManager,
            onSelect = { index ->
                sessionManager.switchTo(index)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onClose = { index -> closeSession(index) }
        )

        sessionRecyclerView.layoutManager = LinearLayoutManager(this)
        sessionRecyclerView.adapter = sessionAdapter

        binding.sessionTabs.onSelect = { index -> sessionManager.switchTo(index) }
        binding.sessionTabs.onClose = { index -> closeSession(index) }
        binding.sessionTabs.onNew = { createNewSession() }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            private var shown = false
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0f && !shown) {
                    shown = true
                    sessionAdapter.notifyDataSetChanged()
                } else if (slideOffset == 0f) {
                    shown = false
                }
            }
        })

        sessionManager.onSessionChanged = { session ->
            runOnUiThread {
                if (session == null) {
                    if (!isBootstrapping && !isFinishing && !isDestroyed) {
                        CortexService.stop(this)
                        finish()
                    }
                } else {
                    CortexService.updateNotification(this)
                    terminalView.session = session
                    sessionAdapter.notifyDataSetChanged()
                    refreshTabs()
                    terminalView.invalidate()
                }
            }
        }

        btnDrawerKeyboard.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            terminalView.showKeyboard()
        }

        btnDrawerNewSession.setOnClickListener {
            createNewSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        applyPreferences()
        val systemReady = !pendingFailsafeSession && DebianRuntime.isInstalled(this)
        if (pendingFailsafeSession) {
            pendingFailsafeSession = false
            createNewSession(failsafeMode = true)
            terminalView.post { terminalView.showKeyboard() }
        } else if (!systemReady && DebianRuntime.isRemoved(this)) {
            showRemovedRootfsDialog()
        } else if (!systemReady) {
            isBootstrapping = true
            val progress = BootstrapDialog(this)
            bootstrapDialog = progress
            try {
                progress?.show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to show progress dialog", e)
            }
            kotlin.concurrent.thread(name = "Miauterm-Debian") {

                val success = try {
                    runBootstrapOnce { percent ->
                        runOnUiThread {
                            if (progress != null) {
                                progress.isIndeterminate = false
                                progress.progress = percent
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "Bootstrap failed", t)
                    false
                }

                runOnUiThread {
                    try {
                        if (progress != null && progress.isShowing) {
                            progress.dismiss()
                        }
                    } catch (e: Exception) {}
                    bootstrapDialog = null
                    isBootstrapping = false
                    if (isFinishing || isDestroyed) {

                        return@runOnUiThread
                    }
                    if (success) {
                        startInitialSession()
                    } else {
                        val detail = DebianRuntime.lastInstallError
                        val message = if (detail.isNullOrBlank()) getString(R.string.debian_install_failed)
                        else getString(R.string.debian_install_failed_details, detail)
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            if (sessionManager.sessionCount() == 0) {
                startInitialSession()
            } else {
                val current = sessionManager.currentSession ?: sessionManager.sessionAt(0)
                terminalView.session = current
                sessionAdapter.notifyDataSetChanged()
                refreshTabs()
                terminalView.invalidate()
            }
            terminalView.post {
                terminalView.showKeyboard()
            }
        }
    }

    private fun closeSession(index: Int) {
        val session = sessionManager.sessionAt(index) ?: return
        if (sessionManager.sessionCount() > 1) {
            sessionManager.removeSession(session)
            sessionAdapter.notifyDataSetChanged()
            refreshTabs()
            CortexService.updateNotification(this)
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    CortexService.instance?.exitAll() ?: run {
                        sessionManager.destroyAll()
                        finish()
                    }
                }
                .setNegativeButton(R.string.no, null)
                .showInChrome()
        }
    }

    private fun refreshTabs() {
        if (!::sessionManager.isInitialized) return
        val sessions = sessionManager.snapshot()
        val tabs = sessions.map { s -> SessionTabsView.Tab(s.promptLabel()) }
        val active = sessionManager.currentSessionIndex
        binding.sessionTabs.update(tabs, active)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForNotificationPrompt && notificationPromptInterruptedActivity) {
            waitingForNotificationPrompt = false
            notificationPromptInterruptedActivity = false

            initializeRuntime()
        }
        applyPreferences()

        extraKeysConfig.setHome(Environment.getHomeDir(this))
        applyExtraKeys()
        extraKeysConfig.watch {
            terminalView.removeCallbacks(applyExtraKeysRunnable)
            terminalView.postDelayed(applyExtraKeysRunnable, 150)
        }
        if (!::sessionManager.isInitialized) return
        val failsafeSessionActive = pendingFailsafeSession || sessionManager.currentSession?.failsafeMode == true
        if (!failsafeSessionActive && DebianRuntime.isRemoved(this) && !removedRootfsNoticeShown) {
            showRemovedRootfsDialog()
        }
        if (!failsafeSessionActive && DebianRuntime.isInstalled(this)) {
            runCatching { MiautermCommandInstaller.install(this, Environment.getCortexRoot(this)) }
                .onFailure { android.util.Log.e("MainActivity", "Could not update miauterm-cmd in Debian", it) }
        }
        val current = sessionManager.currentSession
        if (current != null) {
            terminalView.session = current
        } else if (!isBootstrapping && DebianRuntime.isInstalled(this) && sessionManager.sessionCount() == 0 && !pendingFailsafeSession) {
            restoreOrCreateSession()
        }
        if (!isBootstrapping && pendingFailsafeSession) {
            pendingFailsafeSession = false
            createNewSession(failsafeMode = true)
        }
        if (!isBootstrapping && DebianRuntime.isInstalled(this)) {
            pendingSessionCommand?.let { command ->
                pendingSessionCommand = null

                createNewSession().write("$command\r")
            }
        }
        terminalView.requestLayout()
        terminalView.postInvalidate()
        refreshTabs()
    }

    override fun onStop() {
        super.onStop()

        if (::sessionManager.isInitialized) sessionManager.saveState()
    }

    override fun onPause() {
        super.onPause()
        if (waitingForNotificationPrompt) notificationPromptInterruptedActivity = true
        terminalView.clearSelection()
        extraKeysConfig.stopWatching()
        terminalView.removeCallbacks(applyExtraKeysRunnable)
    }

    internal fun reloadExtraKeys() {
        if (::extraKeysConfig.isInitialized) {
            extraKeysConfig.setHome(Environment.getHomeDir(this))
            applyExtraKeys(force = true)
            extraKeysView.scrollLastRowToEnd()
        }
    }

    private fun applyExtraKeys(force: Boolean = false) {
        if (DebianRuntime.isInstalled(this)) {
            runCatching { extraKeysConfig.ensureConfigFile() }
                .onFailure { android.util.Log.e("MainActivity", "Could not create the default extra-keys file", it) }
        }
        val result = if (force) extraKeysConfig.reload() else extraKeysConfig.loadIfChanged() ?: return
        val rows = result.rows.ifEmpty { ExtraKeysLayout.DEFAULT_ROWS }
        extraKeysView.setKeys(rows)
        val bar = binding.bottomBarContainer
        val height = (ExtraKeysView.barHeightDp(rows.size) * resources.displayMetrics.density).toInt()
        if (bar.layoutParams.height != height) {
            bar.layoutParams = bar.layoutParams.apply { this.height = height }
        }
        extraKeysView.scrollLastRowToEnd()
        result.error?.let {
            Toast.makeText(this, getString(R.string.extra_keys_error, it), Toast.LENGTH_LONG).show()
        }
    }

    internal fun reloadTerminalFont(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val fontKey = prefs.getString("terminal_font", "jetbrains_mono") ?: "jetbrains_mono"
        return terminalView.setTerminalFont(fontKey, forceReload = true)
    }

    private fun applyPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val fontSizeStr = prefs.getString("terminal_font_size", "11") ?: "11"
        val fontSize = fontSizeStr.toFloatOrNull() ?: 11f
        terminalView.setTerminalTextSize(fontSize)

        val themeKey = prefs.getString("terminal_theme", "catppuccin") ?: "catppuccin"
        org.cortex.terminal.emulator.TerminalColor.applyTheme(themeKey)

        org.cortex.terminal.emulator.TerminalColor.takeRemap()?.let { remap ->
            if (::sessionManager.isInitialized) {
                sessionManager.snapshot().forEach { it.emulator.remapColors(remap) }
            }
        }
        Chrome.apply(org.cortex.terminal.emulator.TerminalColor.SCHEMES.getValue(org.cortex.terminal.emulator.TerminalColor.currentThemeId))
        terminalView.applyTheme()
        applyChrome()

        val showTabs = prefs.getBoolean("show_session_tabs", true)
        binding.sessionTabs.visibility = if (showTabs) View.VISIBLE else View.GONE
        binding.tabsDivider.visibility = if (showTabs) View.VISIBLE else View.GONE

        val fontKey = prefs.getString("terminal_font", "jetbrains_mono") ?: "jetbrains_mono"
        terminalView.setTerminalFont(fontKey)

        val keepScreenOn = prefs.getBoolean("keep_screen_on", false)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val secureScreen = prefs.getBoolean("secure_screen", false)
        if (secureScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun applyChrome() {
        val p = Chrome.palette
        val density = resources.displayMetrics.density

        window.setBackgroundDrawable(ColorDrawable(p.bg))
        drawerLayout.setBackgroundColor(p.bg)
        drawerLayout.setScrimColor(Chrome.withAlpha(Chrome.BLACK, 150))
        binding.sessionTabs.applyPalette()
        binding.tabsDivider.setBackgroundColor(p.outline)
        binding.dockDivider.setBackgroundColor(p.outline)
        binding.bottomBarContainer.setBackgroundColor(p.bar)
        extraKeysView.applyPalette()
        searchBar.applyPalette()

        binding.navDrawer.setBackgroundColor(p.bar)
        binding.drawerTitle.setTextColor(p.text)
        binding.drawerDivider.setBackgroundColor(p.outline)
        binding.drawerSessionsLabel.setTextColor(p.textFaint)
        fun roundButton() = Chrome.ripple(Chrome.rounded(p.raised, 10 * density), Chrome.withAlpha(p.accent, 55), 10 * density)
        btnDrawerSettings.background = roundButton()
        btnDrawerFiles.background = roundButton()
        btnDrawerSettings.imageTintList = ColorStateList.valueOf(p.textDim)
        btnDrawerFiles.imageTintList = ColorStateList.valueOf(p.textDim)
        btnDrawerKeyboard.background = Chrome.buttonBackground(p, 10 * density, 0, filled = false)
        btnDrawerKeyboard.setTextColor(p.text)
        btnDrawerNewSession.background = Chrome.buttonBackground(p, 10 * density, 0, filled = true)
        btnDrawerNewSession.setTextColor(p.onAccent)
        if (::sessionAdapter.isInitialized) sessionAdapter.notifyDataSetChanged()

        window.statusBarColor = p.bar
        window.navigationBarColor = p.bar
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !p.dark
        controller.isAppearanceLightNavigationBars = !p.dark
    }

    private fun showRemovedRootfsDialog() {
        if (removedRootfsNoticeShown || isFinishing || isDestroyed) return
        removedRootfsNoticeShown = true
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.rootfs_removed_title)
            .setMessage(R.string.rootfs_removed_message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun runBootstrapOnce(onProgress: ((Int) -> Unit)? = null): Boolean {
        val owner = synchronized(bootstrapLock) {
            if (bootstrapInProgress) false else {
                bootstrapInProgress = true
                true
            }
        }
        if (!owner) {
            while (synchronized(bootstrapLock) { bootstrapInProgress }) Thread.sleep(250)
            return DebianRuntime.isInstalled(this)
        }
        return try {
            DebianRuntime.install(this, onProgress)
        } finally {
            synchronized(bootstrapLock) { bootstrapInProgress = false }
        }
    }

    private fun restoreOrCreateSession() {
        if (sessionManager.restore(terminalView.rows, terminalView.cols, terminalView.width, terminalView.height)) {
            terminalView.session = sessionManager.currentSession
            sessionAdapter.notifyDataSetChanged()
            refreshTabs()
            CortexService.updateNotification(this)
        } else {
            createNewSession()
        }
    }

    private fun createNewSession(failsafeMode: Boolean = false): TerminalSession {
        val session = sessionManager.newSession(
            rows = terminalView.rows,
            cols = terminalView.cols,
            widthPx = terminalView.width,
            heightPx = terminalView.height,
            failsafeMode = failsafeMode
        )
        terminalView.session = session
        sessionAdapter.notifyDataSetChanged()
        refreshTabs()
        return session
    }

    private var isSearchAnimating = false

    private fun showTerminalSearch(animated: Boolean = true) {
        if (searchBar.visibility == View.VISIBLE && !isSearchAnimating) return
        if (isSearchAnimating) return

        val width = extraKeysView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val widthF = width.toFloat()

        if (!animated) {
            extraKeysView.visibility = View.GONE
            extraKeysView.translationX = 0f
            searchBar.visibility = View.VISIBLE
            searchBar.translationX = 0f
            searchBar.alpha = 1f
            searchBar.focusInput()
            return
        }

        isSearchAnimating = true

        searchBar.visibility = View.VISIBLE
        searchBar.translationX = widthF
        searchBar.alpha = 0f

        extraKeysView.animate()
            .translationX(-widthF)
            .alpha(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                extraKeysView.visibility = View.GONE
                extraKeysView.translationX = 0f
                extraKeysView.alpha = 1f
            }
            .start()

        searchBar.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isSearchAnimating = false
                searchBar.focusInput()
            }
            .start()
    }

    private fun hideTerminalSearch(animated: Boolean = true) {
        if (searchBar.visibility != View.VISIBLE && !isSearchAnimating) return
        if (isSearchAnimating) return

        val width = searchBar.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val widthF = width.toFloat()

        searchBar.clearInputAndKeyboard()

        if (!animated) {
            searchBar.visibility = View.GONE
            searchBar.translationX = 0f
            terminalView.clearSearch()
            extraKeysView.visibility = View.VISIBLE
            extraKeysView.translationX = 0f
            extraKeysView.alpha = 1f
            return
        }

        isSearchAnimating = true

        extraKeysView.visibility = View.VISIBLE
        extraKeysView.translationX = -widthF
        extraKeysView.alpha = 0f

        searchBar.animate()
            .translationX(widthF)
            .alpha(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                searchBar.visibility = View.GONE
                searchBar.translationX = 0f
                searchBar.alpha = 1f
                terminalView.clearSearch()
            }
            .start()

        extraKeysView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isSearchAnimating = false
            }
            .start()
    }

    private fun toggleTerminalSearch() {
        if (searchBar.visibility == View.VISIBLE) {
            hideTerminalSearch(animated = true)
        } else {
            showTerminalSearch(animated = true)
        }
    }

    private fun setupBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchBar.visibility == View.VISIBLE) {
                    hideTerminalSearch(animated = true)
                    return
                }
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                if (sessionManager.sessionCount() > 1) {
                    val current = sessionManager.currentSession
                    if (current != null) {
                        sessionManager.removeSession(current)
                        sessionAdapter.notifyDataSetChanged()
                        refreshTabs()
                        return
                    }
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.exit_confirm_title)
                    .setMessage(R.string.exit_confirm_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        CortexService.instance?.exitAll() ?: run {
                            sessionManager.destroyAll()
                            finish()
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .showInChrome()
            }
        })
    }

    private fun startInitialSession() {
        applyExtraKeys()
        openInitialSession()
    }

    private fun openInitialSession() {
        restoreOrCreateSession()
        terminalView.post { terminalView.showKeyboard() }
    }

    override fun onDestroy() {
        try {
            if (bootstrapDialog?.isShowing == true) {
                bootstrapDialog?.dismiss()
            }
        } catch (e: Exception) {}
        bootstrapDialog = null
        if (::sessionManager.isInitialized) {
            sessionManager.onSessionChanged = null
        }
        try { if (searchBar.isShowing()) searchBar.hide() } catch (_: Exception) {}
        terminalView.onUrlTapped = null
        terminalView.onTextSizeChangedByPinch = null
        terminalView.onSearchChanged = null
        terminalView.session = null
        if (instance == this) instance = null
        if (runtimeInitializationStarted && ::sessionManager.isInitialized && sessionManager.sessionCount() == 0) {
            CortexService.stop(this)
        }
        super.onDestroy()
    }
}
