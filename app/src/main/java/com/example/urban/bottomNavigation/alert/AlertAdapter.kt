package com.example.urban.bottomNavigation.alert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter(
    private var items: List<AlertItem> = emptyList(),
    private val click: (AlertItem) -> Unit
) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = items[position]
        holder.binding.tvAlertTitle.text = alert.title
        holder.binding.tvAlertBody.text = alert.body
        holder.binding.tvAlertMeta.text = buildMeta(alert)
        holder.binding.tvAlertType.text = alert.type
        holder.binding.viewUnreadDot.visibility = if (alert.isRead) View.GONE else View.VISIBLE

        holder.binding.tvAlertType.setBackgroundResource(
            when (alert.type) {
                "SLA Warning" -> R.drawable.bg_alert_type_warning
                "Assignment" -> R.drawable.bg_alert_type_assignment
                "Resolved" -> R.drawable.bg_alert_type_resolved
                else -> R.drawable.bg_alert_type_default
            }
        )

        holder.itemView.alpha = if (alert.isRead) 0.82f else 1f
        holder.itemView.setOnClickListener { click(alert) }
    }

    fun updateItems(newItems: List<AlertItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun buildMeta(alert: AlertItem): String {
        val dateLabel = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(alert.timestamp))
        return if (alert.complaintDisplayId.isNotBlank()) {
            "${alert.complaintDisplayId} • $dateLabel"
        } else {
            dateLabel
        }
    }
}
