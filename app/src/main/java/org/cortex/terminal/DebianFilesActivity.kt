package org.cortex.terminal

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.system.Os
import android.system.OsConstants
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.cortex.terminal.runtime.Environment
import java.io.File

class DebianFilesActivity : AppCompatActivity() {
    private lateinit var root: File
    private var current: File? = null
    private lateinit var pathView: TextView
    private lateinit var summaryView: TextView
    private lateinit var emptyView: TextView
    private lateinit var filesList: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debian_files)
        root = Environment.getCortexRoot(this).canonicalFile
        pathView = findViewById(R.id.filesPath)
        summaryView = findViewById(R.id.filesSummary)
        emptyView = findViewById(R.id.filesEmpty)
        filesList = findViewById(R.id.filesList)
        toolbar = findViewById(R.id.filesToolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { if (current != null) goUp() else finish() }
        toolbar.setNavigationIconTint(org.cortex.terminal.view.Chrome.palette.text)
        toolbar.setTitleTextColor(org.cortex.terminal.view.Chrome.palette.text)
        toolbar.setBackgroundColor(org.cortex.terminal.view.Chrome.palette.bar)
        toolbar.menu.add(0, MENU_CREATE_FILE, 0, R.string.files_new_file)
        toolbar.menu.add(0, MENU_CREATE_DIRECTORY, 1, R.string.files_new_folder)
        toolbar.menu.add(0, MENU_UP, 2, R.string.files_go_up)
        toolbar.menu.add(0, MENU_REFRESH, 3, R.string.files_refresh)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE_FILE -> createEntry(isDirectory = false)
                MENU_CREATE_DIRECTORY -> createEntry(isDirectory = true)
                MENU_UP -> goUp()
                MENU_REFRESH -> showDirectory()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        findViewById<View>(R.id.filesRoot).setBackgroundColor(org.cortex.terminal.view.Chrome.palette.bg)
        adapter = FileAdapter(::openItem, ::showItemActions)
        filesList.apply {
            layoutManager = LinearLayoutManager(this@DebianFilesActivity)
            adapter = this@DebianFilesActivity.adapter
            setHasFixedSize(false)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (current != null) {
                    goUp()
                } else finish()
            }
        })
        savedInstanceState?.getString(STATE_CURRENT)?.let { relative ->
            val restored = File(root, relative)
            if (isInsideRoot(restored) && restored.isDirectory) current = restored
        }
        showDirectory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        current?.let { outState.putString(STATE_CURRENT, it.relativeTo(root).invariantSeparatorsPath) }
    }

    private fun goUp() {
        val directory = current ?: return
        current = if (directory == root) null else directory.parentFile?.takeIf { isInsideRoot(it) }
        showDirectory()
    }

    private fun isInsideRoot(file: File): Boolean {
        return try {
            val path = file.canonicalFile.path
            path == root.path || path.startsWith(root.path + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun showDirectory() {
        val directory = current
        toolbar.menu.findItem(MENU_CREATE_FILE)?.isVisible = directory != null
        toolbar.menu.findItem(MENU_CREATE_DIRECTORY)?.isVisible = directory != null
        toolbar.menu.findItem(MENU_UP)?.isVisible = directory != null
        if (directory == null) {
            pathView.text = getString(R.string.files_locations)
            summaryView.text = getString(R.string.files_locations_summary)
            val entries = mutableListOf(
                Entry(root, runCatching { Os.lstat(root.absolutePath).st_mode }.getOrDefault(OsConstants.S_IFDIR), shortcut = true)
            )
            val androidFolder = File(root, "root/android")
            if (androidFolder.isDirectory && !org.cortex.terminal.runtime.ElfLinkerPatcher.isSymlink(androidFolder)) {
                entries += Entry(
                    androidFolder,
                    runCatching { Os.lstat(androidFolder.absolutePath).st_mode }.getOrDefault(OsConstants.S_IFDIR),
                    shortcut = true,
                    label = "android"
                )
            }
            adapter.submitList(entries)
            showEmptyState(null)
            return
        }
        if (!isInsideRoot(directory)) {
            current = null
            showDirectory()
            return
        }
        val children = try {
            directory.listFiles()?.mapNotNull { file ->
                runCatching { Entry(file, Os.lstat(file.absolutePath).st_mode) }.getOrNull()
            }?.sortedWith(compareBy<Entry>({ !it.isDirectory }, { it.file.name.lowercase() }))
        } catch (_: Exception) {
            null
        }
        val relative = if (directory == root) "/" else "/${directory.relativeTo(root).path}"
        pathView.text = "proot ($relative)"
        if (children == null) {
            summaryView.text = ""
            adapter.submitList(emptyList())
            showEmptyState(getString(R.string.files_list_error))
            return
        }
        summaryView.text = getString(R.string.files_directory_summary, children.size)
        adapter.submitList(children)
        showEmptyState(if (children.isEmpty()) getString(R.string.files_empty) else null)
    }

    private fun showEmptyState(message: String?) {
        emptyView.text = message.orEmpty()
        emptyView.visibility = if (message == null) View.GONE else View.VISIBLE
        filesList.visibility = if (message == null) View.VISIBLE else View.GONE
    }

    private fun openItem(entry: Entry) {
        when {
            entry.isDirectory -> {
                current = entry.file
                showDirectory()
            }
            entry.isSymlink -> Toast.makeText(
                this,
                "${entry.file.name} → ${runCatching { Os.readlink(entry.file.absolutePath) }.getOrDefault("")}",
                Toast.LENGTH_LONG
            ).show()
            entry.isRegular -> showPreview(entry.file, entry.mode)
        }
    }

    private fun showItemActions(entry: Entry) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (entry.isDirectory) {
            labels += getString(R.string.files_open)
            actions += { current = entry.file; showDirectory() }
        }
        if (entry.isRegular) {
            labels += getString(R.string.files_preview)
            actions += { showPreview(entry.file, entry.mode) }
            if (entry.writable) {
                labels += getString(R.string.files_edit)
                actions += { editFile(entry.file, entry.mode) }
            }
        }
        labels += getString(R.string.files_copy_path)
        actions += { copyPath(entry.file) }
        if (!entry.shortcut && entry.file != root && canModifyParent(entry.file)) {
            labels += getString(R.string.files_rename)
            actions += { renameEntry(entry.file) }
            labels += getString(R.string.files_delete)
            actions += { deleteEntry(entry.file) }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(entry.file.name.ifEmpty { "/" })
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        org.cortex.terminal.view.ChromeDialog.style(dialog).show()
    }

    private fun createEntry(isDirectory: Boolean) {
        val directory = current ?: return
        if (!isWritableDirectory(directory)) {
            showMessage(getString(R.string.files_read_only_folder))
            return
        }
        promptForName(
            title = getString(if (isDirectory) R.string.files_new_folder else R.string.files_new_file),
            initial = ""
        ) { name ->
            val target = File(directory, name)
            if (!isInsideRoot(target) || target.exists()) {
                showMessage(getString(R.string.files_name_exists))
                return@promptForName
            }
            val result = runCatching { if (isDirectory) target.mkdir() else target.createNewFile() }
            if (result.getOrDefault(false)) showDirectory()
            else showMessage(result.exceptionOrNull()?.message ?: getString(R.string.files_operation_failed))
        }
    }

    private fun renameEntry(file: File) {
        val parent = file.parentFile ?: return
        if (!isInsideRoot(parent) || !canModifyParent(file)) return
        promptForName(getString(R.string.files_rename), file.name) { name ->
            val destination = File(parent, name)
            if (!isInsideRoot(parent) || destination.exists()) {
                showMessage(getString(R.string.files_name_exists))
                return@promptForName
            }
            if (!file.renameTo(destination)) showMessage(getString(R.string.files_operation_failed)) else showDirectory()
        }
    }

    private fun deleteEntry(file: File) {
        val parent = file.parentFile ?: return
        if (file == root || !isInsideRoot(parent) || !canModifyParent(file)) return
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.files_delete)
            .setMessage(getString(R.string.files_delete_confirm, file.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.files_delete) { _, _ ->

                Thread({
                    val deleted = org.cortex.terminal.runtime.SafeDelete.deleteRecursively(file)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            if (deleted) showDirectory()
                            else showMessage(getString(R.string.files_operation_failed))
                        }
                    }
                }, "Miauterm-FileDelete").start()
            }
            .create()
        org.cortex.terminal.view.ChromeDialog.style(dialog).show()
    }

    private fun editFile(file: File, mode: Int) {
        runCatching {
            require(isInsideRoot(file) && file.isFile) { getString(R.string.files_unavailable) }
            require(mode and 0x92 != 0 && file.canWrite()) { getString(R.string.files_read_only) }
            require(file.length() <= EDIT_LIMIT) { getString(R.string.files_edit_too_large) }
            val bytes = file.readBytes()
            require(bytes.none { it == 0.toByte() }) { getString(R.string.files_binary_edit_unavailable) }
            val editor = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                typeface = android.graphics.Typeface.MONOSPACE
                minLines = 10
                maxLines = 18
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                setText(bytes.toString(Charsets.UTF_8))
                setSelection(text.length)
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.files_edit_title, file.name))
                .setView(editor)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create()
            org.cortex.terminal.view.ChromeDialog.style(dialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = editor.text.toString()
                    if (value.toByteArray(Charsets.UTF_8).size > EDIT_LIMIT) {
                        editor.error = getString(R.string.files_edit_too_large)
                    } else {
                        runCatching {
                            require(isInsideRoot(file) && mode and 0x92 != 0 && file.canWrite())
                            file.writeText(value, Charsets.UTF_8)
                        }.onSuccess {
                            dialog.dismiss()
                            showDirectory()
                            showMessage(getString(R.string.files_saved))
                        }.onFailure { showMessage(it.message ?: getString(R.string.files_operation_failed)) }
                    }
                }
            }.show()
        }.onFailure { showMessage(it.message ?: getString(R.string.files_unavailable)) }
    }

    private fun promptForName(title: String, initial: String, onSubmit: (String) -> Unit) {
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
            setSelection(text.length)
            setPadding(24, 12, 24, 12)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        org.cortex.terminal.view.ChromeDialog.style(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (!validName(name)) {
                    input.error = getString(R.string.files_invalid_name)
                } else {
                    onSubmit(name)
                    if (input.error == null) dialog.dismiss()
                }
            }
        }.show()
    }

    private fun validName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\u0000')

    private fun isWritableDirectory(directory: File): Boolean {
        if (!isInsideRoot(directory) || !directory.isDirectory || !directory.canWrite()) return false
        return runCatching { Os.stat(directory.absolutePath).st_mode and 0x92 != 0 }.getOrDefault(false)
    }

    private fun canModifyParent(file: File): Boolean = file.parentFile?.let(::isWritableDirectory) == true

    private fun copyPath(file: File) {
        val path = if (file == root) "/" else "/${file.relativeTo(root).path}"
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Debian path", path))
        showMessage(getString(R.string.files_path_copied, path))
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showPreview(file: File, mode: Int) {
        runCatching {
            require(isInsideRoot(file))
            require(file.length() <= PREVIEW_LIMIT) { getString(R.string.files_preview_too_large) }
            val bytes = file.readBytes()
            require(bytes.none { it == 0.toByte() }) { "Binary file preview is unavailable" }
            val text = bytes.toString(Charsets.UTF_8)
            val content = TextView(this).apply {
                setTextColor(org.cortex.terminal.view.Chrome.palette.text)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(18, 14, 18, 14)
                this.text = text
            }
            val scroll = android.widget.ScrollView(this).apply { addView(content) }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(file.name)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show().also { org.cortex.terminal.view.ChromeDialog.style(it) }
        }.onFailure { error ->
            android.widget.Toast.makeText(this, error.message ?: getString(R.string.files_unavailable), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private data class Entry(val file: File, val mode: Int, val shortcut: Boolean = false, val label: String = file.name) {
        private val kind get() = mode and OsConstants.S_IFMT
        val isDirectory get() = kind == OsConstants.S_IFDIR
        val isSymlink get() = kind == OsConstants.S_IFLNK
        val isRegular get() = kind == OsConstants.S_IFREG
        val writable get() = mode and 0x92 != 0
    }

    private inner class FileAdapter(
        private val onClick: (Entry) -> Unit,
        private val onLongClick: (Entry) -> Unit
    ) : RecyclerView.Adapter<FileHolder>() {
        private var entries: List<Entry> = emptyList()
        fun submitList(next: List<Entry>) {
            entries = next
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder = FileHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_debian_file, parent, false)
        )
        override fun getItemCount() = entries.size
        override fun onBindViewHolder(holder: FileHolder, position: Int) =
            holder.bind(entries[position], onClick, onLongClick)
    }

    private inner class FileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.fileIcon)
        private val name = view.findViewById<TextView>(R.id.fileName)
        private val details = view.findViewById<TextView>(R.id.fileDetails)
        private val actions = view.findViewById<ImageButton>(R.id.fileActions)
        fun bind(entry: Entry, click: (Entry) -> Unit, longClick: (Entry) -> Unit) {
            val p = org.cortex.terminal.view.Chrome.palette
            name.text = if (entry.shortcut && entry.file == root) "proot (/)" else entry.label
            name.setTextColor(p.text)
            details.setTextColor(p.textDim)
            val mode = entry.mode and 0x1ff
            icon.visibility = View.VISIBLE
            icon.setImageResource(if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
            icon.setColorFilter(if (entry.isDirectory) p.accent else p.textDim)
            when {
                entry.shortcut -> {
                    details.text = if (entry.file == root) getString(R.string.files_debian_location)
                    else getString(R.string.files_android_location)
                }
                entry.isDirectory -> {
                    details.text = getString(
                        R.string.files_entry_details,
                        getString(R.string.files_folder_type),
                        getString(R.string.files_mode_summary, mode),
                        android.text.format.DateFormat.getDateFormat(this@DebianFilesActivity).format(entry.file.lastModified())
                    )
                }
                entry.isSymlink -> {
                    details.text = getString(R.string.files_symlink)
                }
                else -> {
                    details.text = getString(
                        R.string.files_entry_details,
                        Formatter.formatFileSize(this@DebianFilesActivity, entry.file.length()),
                        getString(R.string.files_mode_summary, mode),
                        android.text.format.DateFormat.getDateFormat(this@DebianFilesActivity).format(entry.file.lastModified())
                    )
                }
            }
            itemView.setOnClickListener { click(entry) }
            itemView.isClickable = true
            itemView.setOnLongClickListener {
                longClick(entry)
                true
            }
            itemView.contentDescription = "${name.text}, ${details.text}"
            actions.contentDescription = getString(R.string.files_more_actions, name.text)
            actions.imageTintList = android.content.res.ColorStateList.valueOf(p.textDim)
            actions.setOnClickListener { longClick(entry) }
        }
    }

    companion object {
        private const val STATE_CURRENT = "current_directory"
        private const val PREVIEW_LIMIT = 256 * 1024L
        private const val EDIT_LIMIT = 512 * 1024
        private const val MENU_CREATE_FILE = 1
        private const val MENU_CREATE_DIRECTORY = 2
        private const val MENU_UP = 3
        private const val MENU_REFRESH = 4
    }
}
