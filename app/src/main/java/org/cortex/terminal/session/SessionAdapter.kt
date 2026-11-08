package org.cortex.terminal.session

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.cortex.terminal.databinding.ItemSessionBinding
import org.cortex.terminal.view.Chrome

class SessionAdapter(
    private val sessionManager: SessionManager,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isActive = position == sessionManager.currentSessionIndex
        val binding = holder.binding
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val p = Chrome.palette

        val session = synchronized(sessionManager.sessions) { sessionManager.sessions.getOrNull(position) }
        binding.sessionLabelText.text = sessionPromptLabel(session?.accountName ?: "root")

        val radius = 10 * density
        binding.sessionItemRoot.background = Chrome.ripple(
            if (isActive) Chrome.rounded(p.accentSoft, radius)
            else Chrome.rounded(0, radius),
            Chrome.withAlpha(p.accent, 50),
            radius
        )
        binding.sessionLabelText.setTextColor(p.text)

        binding.btnSessionClose.visibility = View.VISIBLE
        binding.btnSessionClose.setTextColor(p.textDim)
        binding.btnSessionClose.background = Chrome.ripple(Chrome.rounded(0, 12 * density), Chrome.withAlpha(p.danger, 70), 12 * density)
        binding.btnSessionClose.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onClose(pos)
            }
        }

        binding.sessionItemRoot.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onSelect(pos)
            }
        }
    }

    override fun getItemCount(): Int = sessionManager.sessionCount()
}
