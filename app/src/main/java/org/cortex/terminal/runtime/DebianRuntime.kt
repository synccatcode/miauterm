package org.cortex.terminal.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment as AndroidEnvironment
import androidx.preference.PreferenceManager
import android.util.Log
import org.cortex.terminal.api.ApiServer
import org.cortex.terminal.pty.PtyNative
import java.io.File
import java.net.InetAddress

object DebianRuntime {
    private const val TAG = "DebianRuntime"
    private const val RESOLV_CONF_HEADER = "# Managed by Miauterm. Android network DNS servers.\n"
    private const val VERSION = 15
    private const val ROOTFS_ASSET = "debian-arm64.tar.zst"
    private const val PROOT_ASSET_DIR = "proot/arm64-v8a"
    private val prootFiles = listOf("proot", "loader", "libtalloc.so.2", "libandroid-shmem.so")
    private val essentialCommands = listOf(
        "usr/bin/passwd",
        "usr/sbin/useradd",
        "usr/sbin/groupadd",
        "usr/sbin/userdel",
        "usr/sbin/usermod",
        "usr/bin/chown",
        "usr/bin/dpkg",
        "usr/bin/apt",
        "usr/bin/apt-get"
    )
    private val networkCommands = listOf(
        "usr/bin/ping",
        "usr/sbin/ip",
        "usr/bin/ss",
        "usr/sbin/ifconfig",
        "usr/sbin/route",
        "usr/bin/netstat",
        "usr/sbin/arp",
        "usr/bin/curl",
        "usr/bin/wget"
    )
    private val networkPackages = listOf("iputils-ping", "iproute2", "net-tools", "curl", "wget")
    const val PREF_PROOT_DIAGNOSTICS = "proot_diagnostics"
    const val PREF_ROOTFS_REMOVED = "rootfs_removed"
    @Volatile
    var lastInstallError: String? = null
        private set
    private const val LOG_LIMIT = 2 * 1024 * 1024L

    fun isRemoved(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PREF_ROOTFS_REMOVED, false)

    fun isInstalled(context: Context): Boolean {
        if (isRemoved(context)) return false
        val root = Environment.getCortexRoot(context)
        val marker = File(root, ".miauterm-version")
        return marker.isFile && marker.readText().trim() == VERSION.toString() &&
            File(root, "root").isDirectory && systemName(root) != null && shellPath(root) != null &&
            invalidEssentialCommand(root) == null && invalidNetworkCommand(root) == null &&
            MiautermCommandInstaller.isInstalled(root) &&
            prootFiles.all { File(context.filesDir, "proot/$it").isFile } &&
            File(context.filesDir, "proot/proot").canExecute()
    }

    fun install(context: Context, onProgress: ((Int) -> Unit)? = null, force: Boolean = false): Boolean {
        val root = Environment.getCortexRoot(context)
        val runtime = File(context.filesDir, "proot")
        val archive = File(context.cacheDir, ROOTFS_ASSET)
        val staging = File(context.filesDir, "debian-install-staging")
        val previous = File(context.filesDir, "debian-install-previous")
        return try {
            onProgress?.invoke(2)
            installProot(context, runtime)

            if (!force && root.exists()) {
                val existingRootWorks = runCatching {
                    verifyRootfs(root)
                    prepareHome(root)
                    MiautermCommandInstaller.install(context, root)
                    verifyProotStartsBash(context, root)
                }.isSuccess
                if (existingRootWorks) {
                    onProgress?.invoke(92)
                    ensureNetworkTools(context, root)
                    File(root, ".miauterm-version").writeText("$VERSION\n")
                    File(context.filesDir, "rootfs-original.tar.zst").delete()
                    File(context.filesDir, "rootfs-original.installing").delete()
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .remove(PREF_ROOTFS_REMOVED).commit()
                    lastInstallError = null
                    onProgress?.invoke(100)
                    return true
                }
            }

            if (!SafeDelete.deleteRecursively(staging)) {
                throw IllegalStateException("Could not clear the rootfs staging directory")
            }
            staging.mkdirs()
            context.assets.open(ROOTFS_ASSET).use { input ->
                archive.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            onProgress?.invoke(12)
            val result = PtyNative.extractTar(archive.absolutePath, staging.absolutePath) { percent ->
                onProgress?.invoke(percent)
            }
            if (result != 0) throw IllegalStateException("Rootfs archive extraction failed ($result)")
            onProgress?.invoke(92)
            verifyRootfs(staging)
            prepareHome(staging)
            MiautermCommandInstaller.install(context, staging)
            ensureNetworkTools(context, staging)
            verifyProotStartsBash(context, staging)
            File(staging, ".miauterm-version").writeText("$VERSION\n")

            if (!SafeDelete.deleteRecursively(previous)) {
                throw IllegalStateException("Could not clear the previous rootfs")
            }
            if (root.exists() && !root.renameTo(previous)) {
                throw IllegalStateException("Could not move the current Linux rootfs")
            }
            if (!staging.renameTo(root)) {
                previous.renameTo(root)
                throw IllegalStateException("Could not install the verified rootfs")
            }
            SafeDelete.deleteRecursively(previous)
            File(context.filesDir, "rootfs-original.tar.zst").delete()
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(PREF_ROOTFS_REMOVED).commit()
            lastInstallError = null
            onProgress?.invoke(100)
            true
        } catch (e: Exception) {
            lastInstallError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Could not install the Linux rootfs", e)
            false
        } finally {
            archive.delete()
            SafeDelete.deleteRecursively(staging)
        }
    }

    fun removeDebian(context: Context) {
        val root = Environment.getCortexRoot(context)
        val extras = listOf(
            root,
            File(context.filesDir, "debian-reset-staging"),
            File(context.filesDir, "debian-install-staging"),
            File(context.filesDir, "debian-install-previous"),
            File(context.filesDir, "debian-restore-staging"),
            File(context.filesDir, "debian-restore-previous"),
            File(context.filesDir, "rootfs-original.tar.zst"),
            File(context.filesDir, "rootfs-original.installing")
        )
        extras.forEach { path ->
            if (!SafeDelete.deleteRecursively(path)) error("Could not remove ${path.name}")
        }
        File(context.filesDir, "proot/proot.log").delete()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_ROOTFS_REMOVED, true).commit()
    }

    internal fun prepareHome(root: File) {
        val rootHome = File(root, "root")
        val legacyCortexConfig = File(rootHome, ".cortex")
        if (legacyCortexConfig.isDirectory) {
            val configDir = File(rootHome, ".config/miauterm")
            val known = legacyCortexConfig.listFiles().orEmpty()
            configDir.mkdirs()
            known.filter { it.isFile && it.name in setOf("keys", "keys.example", "font.ttf") }.forEach { old ->
                val target = File(configDir, old.name)
                if (!target.exists()) old.copyTo(target)
            }
            SafeDelete.deleteRecursively(legacyCortexConfig)
        }
        removeUnusedAndroidPlaceholders(rootHome)
        migrateManagedReadme(root, rootHome)

        val devDirectory = File(root, "dev")
        check(!ElfLinkerPatcher.isSymlink(devDirectory) && (devDirectory.isDirectory || devDirectory.mkdirs())) {
            "Could not create /dev in Debian"
        }
        val devPts = File(devDirectory, "pts")
        check(!ElfLinkerPatcher.isSymlink(devPts) && (devPts.isDirectory || devPts.mkdirs())) {
            "Could not create /dev/pts in Debian"
        }
        val ptmx = File(root, "dev/ptmx")
        if (!ptmx.exists()) check(ElfLinkerPatcher.createSymlink("pts/ptmx", ptmx)) {
            "Could not link /dev/ptmx to /dev/pts/ptmx"
        }

        val profileDirectory = File(root, "etc/profile.d")
        check(profileDirectory.isDirectory || profileDirectory.mkdirs()) {
            "Could not create the Linux login profile directory"
        }
        val loginDefs = File(root, "etc/login.defs")
        check(!ElfLinkerPatcher.isSymlink(loginDefs)) { "Debian /etc/login.defs must not be a symlink" }
        val loginDefsText = if (loginDefs.isFile) loginDefs.readText() else ""
        val createHome = Regex("(?m)^[ \\t]*CREATE_HOME[ \\t]+[^\\r\\n]*")
        val updatedLoginDefs = if (createHome.containsMatchIn(loginDefsText)) {
            createHome.replace(loginDefsText, "CREATE_HOME yes")
        } else {
            "${loginDefsText.trimEnd()}${if (loginDefsText.isBlank()) "" else "\n"}CREATE_HOME yes\n"
        }
        if (updatedLoginDefs != loginDefsText) {
            FileInstaller.install(loginDefs, updatedLoginDefs, 420)
        }
        val motd = File(root, "etc/motd")
        if (!ElfLinkerPatcher.isSymlink(motd)) {
            if (!motd.exists() || (motd.isFile && motd.length() == 0L)) {
                motd.writeText("Welcome to Debian in Miauterm.\n\n")
            }
        }
        val motdProfile = File(profileDirectory, "99-miauterm-motd.sh")
        if (!motdProfile.exists()) {
            motdProfile.writeText(
                "case \$- in\n" +
                    "  *i*)\n" +
                    "    if [ -z \"\${MIAUTERM_MOTD_SHOWN:-}\" ]; then\n" +
                    "      export MIAUTERM_MOTD_SHOWN=1\n" +
                    "      [ ! -r /etc/motd ] || cat /etc/motd\n" +
                    "    fi\n" +
                    "    ;;\n" +
                    "esac\n"
            )
        }
        val promptProfile = File(profileDirectory, "99-miauterm-prompt.sh")
        if (!promptProfile.exists()) {
            promptProfile.writeText(
                "# Managed by Miauterm.\n" +
                    "[ -n \"\${BASH_VERSION:-}\" ] || return\n" +
                    "case \$- in *i*) ;; *) return ;; esac\n" +
                    "PS1='\\[\\e[1;32m\\]\\u@localhost\\[\\e[0m\\] \\[\\e[34m\\]\\w\\[\\e[0m\\]\\$ '\n"
            )
        }
        val colorsProfile = File(profileDirectory, "99-miauterm-colors.sh")
        if (!colorsProfile.exists()) {
            colorsProfile.writeText(
                    "case \$- in *i*) ;; *) return ;; esac\n" +
                        "if command -v dircolors >/dev/null 2>&1; then\n" +
                        "  eval \"\$(dircolors -b)\"\n" +
                        "  alias ls='ls --color=auto'\n" +
                        "fi\n"
            )
        }
    }

    private fun removeUnusedAndroidPlaceholders(rootHome: File) {
        val androidDirectory = File(rootHome, "android")
        if (ElfLinkerPatcher.isSymlink(androidDirectory)) {
            check(androidDirectory.delete()) { "Could not remove the unused Android storage link" }
            return
        }
        if (!androidDirectory.isDirectory) return
        val children = androidDirectory.listFiles() ?: return
        val placeholders = children.filter { child ->
            child.name in setOf("Download", "Documents") &&
                child.isDirectory &&
                !ElfLinkerPatcher.isSymlink(child) &&
                child.list()?.isEmpty() == true
        }
        placeholders.forEach { check(it.delete()) { "Could not remove ${it.path}" } }
        if (placeholders.size == children.size && placeholders.isNotEmpty()) {
            check(androidDirectory.delete()) { "Could not remove the unused Android storage directory" }
        }
    }

    private fun migrateManagedReadme(root: File, rootHome: File) {
        val previous = File(root, "README.md")
        val homeReadme = File(rootHome, "README.md")
        var destination = when {
            !homeReadme.exists() || isManagedReadme(homeReadme) -> homeReadme
            else -> File(rootHome, "Miauterm-README.md")
        }
        var suffix = 2
        while (destination.exists() && !isManagedReadme(destination)) {
            destination = File(rootHome, "Miauterm-README-$suffix.md")
            suffix++
        }
        destination.parentFile?.mkdirs()
        destination.writeText(managedReadmeContents())
        if (isManagedReadme(previous)) {
            check(previous.delete()) { "Could not move the Miauterm README from the Debian root" }
        }
    }

    private fun isManagedReadme(file: File): Boolean = runCatching {
        file.isFile && file.readText().startsWith("# Miauterm Linux system")
    }.getOrDefault(false)

    private fun managedReadmeContents(): String =
        "# Miauterm Linux system\n\n" +
            "This Debian system is launched by Miauterm through PRoot. No separate terminal app is required.\n\n" +
            "## Shared folders\n\n" +
            "`~/android` is created only after Download and Documents access is enabled and permission is granted in Miauterm Settings. New Linux sessions mount only those two folders. Changing the setting does not alter running sessions. No Android system directories are mounted.\n\n" +
            "## Terminal startup\n\n" +
            "The MOTD is shown when an interactive shell starts. The prompt shows the current directory in blue, using `~` at home and paths such as `~/projects` as you move into folders. The `root@localhost` part is bold green.\n\n" +
            "## Extra keys\n\n" +
            "Customize keys in `~/.config/miauterm/keys` using nested arrays:\n\n" +
            "```text\n[\n  [\"ESC\", \"/\", \"-\", \"HOME\", \"UP\", \"END\", \"PGUP\"],\n  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]\n]\n```\n\n" +
            "Use Miauterm Settings to edit the keys, view or clear PRoot diagnostics, open a failsafe session, back up or restore files, or reset the bundled Debian system. Export a backup before resetting if you need to keep your files.\n\n" +
            "The `root` shell identity is simulated by PRoot; it is not Android root. Linux account names do not create separate Android security identities.\n"

    internal fun prepareRestoredRootfs(context: Context, root: File) {
        verifyRootfs(root)
        prepareHome(root)
        MiautermCommandInstaller.install(context, root)
        installProot(context, File(context.filesDir, "proot"))
        verifyProotStartsBash(context, root)
        File(root, ".miauterm-version").writeText("$VERSION\n")
    }

    private fun verifyProotStartsBash(context: Context, root: File) {
        val launcher = shellCommand(context)
        val args = arrayOf(
            "--link2symlink", "--kill-on-exit", "--rootfs=${root.absolutePath}",
            "--root-id", "--pwd=/root",
            "/" + shellPath(root)!!.relativeTo(root).invariantSeparatorsPath,
            "-c",
            "/usr/sbin/useradd --help >/dev/null && " +
                "/usr/sbin/groupadd --help >/dev/null && " +
                "/usr/bin/passwd --help >/dev/null && " +
                "/usr/bin/chown --version >/dev/null && " +
                "/usr/bin/dpkg --version >/dev/null && " +
                "/usr/bin/apt --version >/dev/null && " +
                "/usr/bin/apt-get --version >/dev/null"
        )
        val process = ProcessBuilder(listOf(launcher) + args.toList())
            .directory(File(root, "root"))
            .redirectErrorStream(true)
            .apply {
                this.environment().apply {
                    clear()
                    DebianRuntime.environment(context, failsafe = true, rootfs = root).forEach { entry ->
                        val separator = entry.indexOf("=")
                        if (separator > 0) put(entry.substring(0, separator), entry.substring(separator + 1))
                    }
                }
            }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().takeLast(2048).trim() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            output.ifEmpty {
                "PRoot launcher could not start ${shellPath(root)} from ${root.absolutePath} (exit $exitCode)"
            }
        }
    }

    private fun installProot(context: Context, target: File) {
        if (!target.exists()) target.mkdirs()
        prootFiles.forEach { name ->
            val file = File(target, name)
            val needsCopy = !file.isFile || file.length() == 0L ||
                ((name == "proot" || name == "loader") && !file.canExecute()) ||
                (name != "proot" && name != "loader" && !file.canRead())
            if (needsCopy) {
                val temporary = File(target, "$name.installing")
                context.assets.open("$PROOT_ASSET_DIR/$name").use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (file.exists() && !file.delete()) error("Could not replace PRoot runtime file: $name")
                if (!temporary.renameTo(file)) error("Could not install PRoot runtime file: $name")
            }
            if (name == "proot" || name == "loader") {
                check(file.setExecutable(true, true) || file.canExecute()) { "Cannot execute PRoot runtime file: $name" }
            } else {
                check(file.setReadable(true, true) || file.canRead()) { "Cannot read PRoot runtime file: $name" }
            }
        }
    }

    internal fun verifyRootfs(root: File) {
        val osRelease = File(root, "etc/os-release")
        if (!osRelease.isFile || osRelease.readLines().none { it.trim() == "ID=debian" } ||
            !File(root, "etc/debian_version").isFile
        ) {
            throw IllegalStateException("The bundled Debian root filesystem is incomplete or invalid.")
        }
        repairDebianUsrMergeLinks(root)
        val shell = shellPath(root)
            ?: throw IllegalStateException(
                "Debian has no valid ARM64 bash or sh at /usr/bin/bash, /bin/bash, /usr/bin/sh, or /bin/sh."
            )
        check(isArm64Elf(shell)) {
            "The Debian shell is not a little-endian ARM64 executable"
        }
        val invalidCommand = invalidEssentialCommand(root)
        check(invalidCommand == null) {
            "Debian command /$invalidCommand is missing, truncated, or not an executable ARM64 binary."
        }
        check(File(root, "root").isDirectory || File(root, "root").mkdirs()) {
            "Could not prepare /root in Debian"
        }
    }

    private fun invalidEssentialCommand(root: File): String? =
        essentialCommands.firstOrNull { path ->
            val command = File(root, path)
            !command.isFile || command.length() < 4096L ||
                !command.canExecute() || !isArm64Elf(command)
        }

    private fun repairDebianUsrMergeLinks(root: File) {
        for ((name, target) in listOf("bin" to "usr/bin", "sbin" to "usr/sbin", "lib" to "usr/lib")) {
            val link = File(root, name)
            val targetDir = File(root, target)
            if (!targetDir.isDirectory) continue
            if (ElfLinkerPatcher.readSymlink(link) == target) continue
            if (ElfLinkerPatcher.isSymlink(link)) {
                FileInstaller.symlink(target, link)
            } else if (!link.exists()) {
                FileInstaller.symlink(target, link)
            } else if (link.isDirectory && link.list().isNullOrEmpty()) {
                check(link.delete()) { "Could not repair Debian /$name symlink" }
                FileInstaller.symlink(target, link)
            }
        }
    }

    internal fun shellPathForSession(root: File): File? = shellPath(root)

    private fun shellPath(root: File): File? {
        val candidates = listOf("usr/bin/bash", "bin/bash", "usr/bin/sh", "bin/sh")
            .map { File(root, it) }
        candidates.firstOrNull { it.isFile && it.canExecute() }?.let { return it }

        // Rootfs extraction or a later chmod can leave the shell's execute bit unset. Restore it
        // only for a valid ARM64 Linux ELF, never for an arbitrary file in the Debian system.
        val repairable = candidates.firstOrNull { it.isFile && isArm64Elf(it) } ?: return null
        if (repairable.setExecutable(true, true) || repairable.canExecute()) {
            Log.w(TAG, "Restored execute permission on Linux shell ${repairable.absolutePath}")
            return repairable
        }
        Log.e(TAG, "Linux shell exists but cannot be made executable: ${repairable.absolutePath}")
        return null
    }

    private fun isArm64Elf(file: File): Boolean = runCatching {
        val header = ByteArray(20)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count < 0) break
                read += count
            }
            read == header.size && header[0] == 0x7f.toByte() &&
                header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte() && header[4] == 2.toByte() &&
                header[5] == 1.toByte() && header[18] == 183.toByte() && header[19] == 0.toByte()
        }
    }.getOrDefault(false)

    fun systemName(context: Context): String? = systemName(Environment.getCortexRoot(context))

    private fun systemName(root: File): String? {
        val release = File(root, "etc/os-release").takeIf { it.isFile }?.readLines().orEmpty()
        val pretty = release.firstOrNull { it.startsWith("PRETTY_NAME=") }
            ?.substringAfter('=')?.trim()?.trim('\"', '\'')
        if (!pretty.isNullOrBlank()) return pretty
        return release.firstOrNull { it.startsWith("ID=") }?.substringAfter('=')?.trim()?.trim('\"', '\'')
            ?.takeIf { it.isNotBlank() }
    }

    fun shellCommand(context: Context): String {
        val prootDir = File(context.filesDir, "proot")
        val launcher = File(prootDir, "launcher.sh")
        val log = prootLog(context)
        run {
            val proot = File(prootDir, "proot").absolutePath
            val loader = File(prootDir, "loader").absolutePath
            val cache = File(context.cacheDir, "proot-tmp").absolutePath
            launcher.writeText(
                "#!/system/bin/sh\n" +
                    "LOG_FILE='${shellQuote(log.absolutePath)}'\n" +
                    "if [ -f \"\$LOG_FILE\" ] && [ \"$(wc -c < \"\$LOG_FILE\")\" -gt ${LOG_LIMIT} ]; then : > \"\$LOG_FILE\"; fi\n" +
                    "export PROOT_LOADER='${shellQuote(loader)}'\n" +
                    "export PROOT_TMP_DIR='${shellQuote(cache)}'\n" +
                    "export LD_LIBRARY_PATH='${shellQuote(prootDir.absolutePath)}'\n" +
                    "if [ \"\$MIAUTERM_PROOT_DIAGNOSTICS\" = 1 ]; then\n" +
                    "  FIFO=\"\$PROOT_TMP_DIR/proot-log-\$\$\"\n" +
                    "  if /system/bin/toybox mkfifo \"\$FIFO\" 2>/dev/null; then\n" +
                    "    /system/bin/toybox tee -a \"\$LOG_FILE\" < \"\$FIFO\" >&2 & LOGGER=$!\n" +
                    "    '${shellQuote(proot)}' -v 2 \"\$@\" 2>\"\$FIFO\"; STATUS=$?\n" +
                    "    wait \$LOGGER; /system/bin/toybox rm -f \"\$FIFO\"; exit \$STATUS\n" +
                    "  fi\n" +
                    "  exec '${shellQuote(proot)}' -v 2 \"\$@\"\n" +
                    "fi\n" +
                    "exec '${shellQuote(proot)}' \"\$@\"\n"
            )
            launcher.setExecutable(true, true)
        }
        return launcher.absolutePath
    }

    private fun shellQuote(value: String): String = value.replace("'", "'\\''")

    fun prootLog(context: Context): File = File(context.filesDir, "proot/proot.log")

    fun recordRuntimeEvent(context: Context, message: String) {
        if (!diagnosticsEnabled(context)) return
        runCatching {
            val log = prootLog(context)
            log.parentFile?.mkdirs()
            synchronized(DebianRuntime::class.java) {
                if (log.length() > LOG_LIMIT) log.writeText("")
                log.appendText("[${java.time.Instant.now()}] $message\n")
            }
        }.onFailure { Log.w(TAG, "Could not write startup diagnostic", it) }
    }

    fun diagnosticsEnabled(context: Context): Boolean = PreferenceManager
        .getDefaultSharedPreferences(context).getBoolean(PREF_PROOT_DIAGNOSTICS, false)

    fun clearProotLog(context: Context): Boolean {
        val log = prootLog(context)
        return !log.exists() || log.delete()
    }

    fun shellArguments(context: Context, guestDirectory: String?, failsafe: Boolean = false): Array<String> {
        val rootfs = Environment.getCortexRoot(context)
        updateResolvConf(rootfs, networkDnsServers(context))
        val root = rootfs.absolutePath
        val account = selectedAccount(context, rootfs)
        val args = mutableListOf("--link2symlink", "--kill-on-exit", "--rootfs=$root")
        if (account.uid == 0) args += "--root-id" else args += "--change-id=${account.uid}:${account.gid}"
        args += "--pwd=${guestDirectory ?: account.home}"
        if (ApiServer.connectionFile(context).isFile) {
            val connection = ApiServer.connectionDirectory(context)
            val target = File(rootfs, "run/miauterm-cmd")
            check(!ElfLinkerPatcher.isSymlink(target)) {
                "Debian /run/miauterm-cmd must not be a symlink"
            }
            // Older versions bind mounted a single file here; replace it with a directory.
            if (target.exists() && !target.isDirectory) {
                check(target.delete()) { "Could not replace Debian /run/miauterm-cmd" }
            }
            check(target.isDirectory || target.mkdirs()) {
                "Could not create Debian /run/miauterm-cmd"
            }
            args += "--bind=${connection.absolutePath}:/run/miauterm-cmd"
        }
        val devDirectory = File(rootfs, "dev")
        val devPts = File(devDirectory, "pts")
        if (!ElfLinkerPatcher.isSymlink(devDirectory) &&
            (devDirectory.isDirectory || devDirectory.mkdirs()) &&
            !ElfLinkerPatcher.isSymlink(devPts) &&
            (devPts.isDirectory || devPts.mkdirs())
        ) {
            args += "--bind=/dev/pts:/dev/pts"
        } else {
            Log.w(TAG, "Cannot create Debian /dev/pts; terminal process tracking may not work")
        }
        val accountHome = File(rootfs, account.home.removePrefix("/"))
        val androidDirectory = File(accountHome, "android")
        if (!failsafe && isSharedFolderAccessEnabled(context) &&
            !ElfLinkerPatcher.isSymlink(accountHome) &&
            !ElfLinkerPatcher.isSymlink(androidDirectory) &&
            (androidDirectory.isDirectory || androidDirectory.mkdirs())
        ) {
            publicDirectory(context, AndroidEnvironment.DIRECTORY_DOWNLOADS)?.let { host ->
                val guest = File(androidDirectory, "Download")
                if (!ElfLinkerPatcher.isSymlink(guest) && (guest.isDirectory || guest.mkdirs())) {
                    args += "--bind=${host.absolutePath}:${account.home.trimEnd('/')}/android/Download"
                }
            }
            publicDirectory(context, "Documents")?.let { host ->
                val guest = File(androidDirectory, "Documents")
                if (!ElfLinkerPatcher.isSymlink(guest) && (guest.isDirectory || guest.mkdirs())) {
                    args += "--bind=${host.absolutePath}:${account.home.trimEnd('/')}/android/Documents"
                }
            }
        }
        val shell = account.shell.takeIf { it.startsWith("/") && File(rootfs, it.removePrefix("/")).canExecute() }
            ?: shellPath(rootfs)?.let { "/" + it.relativeTo(rootfs).invariantSeparatorsPath }
            ?: "/bin/sh"
        args += listOf(shell, "-l", "-i")
        return args.toTypedArray()
    }

    internal fun updateResolvConf(root: File, dnsServers: List<InetAddress>): Boolean {
        if (dnsServers.isEmpty()) return false

        val etc = File(root, "etc")
        check(!ElfLinkerPatcher.isSymlink(etc) && (etc.isDirectory || etc.mkdirs())) {
            "Could not access Debian /etc for network configuration"
        }
        val resolvConf = File(etc, "resolv.conf")
        if (ElfLinkerPatcher.isSymlink(resolvConf)) return false

        val current = if (resolvConf.isFile) resolvConf.readText() else ""
        if (current.isNotEmpty() && !current.startsWith(RESOLV_CONF_HEADER)) return false
        val servers = dnsServers.map { it.hostAddress }.distinct()
        val updated = buildString {
            append(RESOLV_CONF_HEADER)
            servers.forEach { append("nameserver ").append(it).append('\n') }
        }
        if (updated == current) return false
        return FileInstaller.install(resolvConf, updated, 420)
    }

    internal fun verifyNetworkCommands(root: File) {
        val invalidCommand = invalidNetworkCommand(root)
        check(invalidCommand == null) {
            "Debian network command /$invalidCommand is missing, truncated, or not an executable ARM64 binary."
        }
    }

    private fun invalidNetworkCommand(root: File): String? =
        networkCommands.firstOrNull { path ->
            val command = File(root, path)
            !command.isFile || command.length() < 4096L ||
                !command.canExecute() || !isArm64Elf(command)
        }

    private fun networkDnsServers(context: Context): List<InetAddress> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork ?: return emptyList()
        return connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
    }

    private fun ensureNetworkTools(context: Context, root: File) {
        if (invalidNetworkCommand(root) == null) return
        updateResolvConf(root, networkDnsServers(context))
        val aptOptions = listOf(
            "-o", "Acquire::http::Timeout=30",
            "-o", "Acquire::https::Timeout=30",
            "-o", "Acquire::Retries=1"
        )
        runRootfsCommand(context, root, listOf("/usr/bin/apt-get") + aptOptions + "update")
        runRootfsCommand(
            context,
            root,
            listOf("/usr/bin/apt-get") + aptOptions +
                listOf("install", "-y", "--no-install-recommends") + networkPackages
        )
        verifyNetworkCommands(root)
    }

    private fun runRootfsCommand(context: Context, root: File, command: List<String>) {
        val args = mutableListOf(
            "--link2symlink",
            "--kill-on-exit",
            "--rootfs=${root.absolutePath}",
            "--root-id",
            "--cwd=/root"
        )
        val devDirectory = File(root, "dev")
        val devPts = File(devDirectory, "pts")
        if (!ElfLinkerPatcher.isSymlink(devDirectory) && devDirectory.isDirectory &&
            !ElfLinkerPatcher.isSymlink(devPts) && devPts.isDirectory
        ) {
            args += "--bind=/dev/pts:/dev/pts"
        }
        args += command

        val processBuilder = ProcessBuilder(listOf(shellCommand(context)) + args)
            .directory(File(root, "root"))
            .redirectErrorStream(true)
        val runtimeEnvironment = environment(context, failsafe = true, rootfs = root)
        processBuilder.environment().apply {
            clear()
            runtimeEnvironment.forEach { entry ->
                val separator = entry.indexOf("=")
                if (separator > 0) put(entry.substring(0, separator), entry.substring(separator + 1))
            }
            put("HOME", "/root")
            put("USER", "root")
            put("LOGNAME", "root")
            put("SHELL", "/bin/sh")
            put("LANG", "C")
            put("LC_ALL", "C")
            put("DEBIAN_FRONTEND", "noninteractive")
        }
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText().takeLast(2048).trim() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            output.ifEmpty { "${command.joinToString(" ")} failed (exit $exitCode)" }
        }
    }

    data class LinuxAccount(val name: String, val uid: Int, val gid: Int, val home: String, val shell: String)

    fun accounts(root: File): List<LinuxAccount> {
        val passwd = File(root, "etc/passwd")
        val parsed = runCatching { passwd.readLines().mapNotNull { line ->
            val fields = line.split(':')
            val uid = fields.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val gid = fields.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            val name = fields.getOrNull(0)?.takeIf { it.matches(Regex("[a-z_][a-z0-9_-]*\$?")) } ?: return@mapNotNull null
            LinuxAccount(name, uid, gid, fields.getOrNull(5)?.takeIf { it.startsWith("/") } ?: "/", fields.getOrNull(6).orEmpty())
        } }.getOrDefault(emptyList())
        return listOf(LinuxAccount("root", 0, 0, "/root", "/bin/bash")) + parsed.filter {
            it.uid > 0 && it.uid < 60000 && it.name != "root" &&
                it.shell.startsWith("/") && it.shell !in setOf("/bin/false", "/usr/sbin/nologin", "/sbin/nologin") && File(root, it.shell.removePrefix("/")).canExecute() &&
                File(root, it.home.removePrefix("/")).isDirectory
        }.distinctBy { it.name }
    }

    fun selectedAccount(context: Context, root: File = Environment.getCortexRoot(context)): LinuxAccount {
        val wanted = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_LINUX_USER, "")
            ?.trim().orEmpty().ifBlank { "root" }
        val available = accounts(root)
        return available.firstOrNull { it.name == wanted } ?: available.first()
    }

    const val PREF_LINUX_USER = "linux_user"
    const val PREF_SHARED_FOLDER_ACCESS = "shared_folder_access_enabled"

    fun hasSharedFolderPermission(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
        return context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == granted &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == granted
    }

    fun isSharedFolderAccessEnabled(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val permissionGranted = hasSharedFolderPermission(context)
        if (!preferences.contains(PREF_SHARED_FOLDER_ACCESS)) {
            preferences.edit().putBoolean(PREF_SHARED_FOLDER_ACCESS, permissionGranted).apply()
        }
        return permissionGranted && preferences.getBoolean(PREF_SHARED_FOLDER_ACCESS, false)
    }

    private fun publicDirectory(context: Context, name: String): File? {
        if (!isSharedFolderAccessEnabled(context)) return null
        val directory = File(AndroidEnvironment.getExternalStorageDirectory(), name)
        if (!directory.exists() && !directory.mkdirs()) return null
        return directory.takeIf { it.isDirectory && it.canRead() }
    }

    fun environment(context: Context, failsafe: Boolean = false, rootfs: File = Environment.getCortexRoot(context)): Array<String> {
        val proot = File(context.filesDir, "proot")
        val prootTemp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val tz = java.util.TimeZone.getDefault().id
        val account = selectedAccount(context, rootfs)
        return arrayOf(
            "HOME=${account.home}",
            "USER=${account.name}",
            "LOGNAME=${account.name}",
            "SHELL=${account.shell.ifBlank { shellPath(rootfs)?.let { "/" + it.relativeTo(rootfs).invariantSeparatorsPath } ?: "/bin/sh" }}",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TMPDIR=/tmp",
            "TZ=$tz",
            "PROOT_LOADER=${File(proot, "loader").absolutePath}",
            "PROOT_TMP_DIR=${prootTemp.absolutePath}",
            "LD_LIBRARY_PATH=${proot.absolutePath}",
            "MIAUTERM_PROOT_DIAGNOSTICS=${if (diagnosticsEnabled(context)) 1 else 0}"
        ) + if (failsafe) arrayOf("PROOT_NO_SECCOMP=1") else emptyArray()
    }
}
