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

    // This holder keeps references to the alert item views used by the RecyclerView.
    inner class AlertViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    // This function creates the view holder for one alert row.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    // This function tells the RecyclerView how many alerts need to be shown.
    override fun getItemCount(): Int = items.size

    // This function binds one alert item to the visible row UI.
    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = items[position]
        holder.binding.tvAlertTitle.text = localizedTitle(holder, alert)
        holder.binding.tvAlertBody.text = localizedBody(holder, alert)
        holder.binding.tvAlertMeta.text = buildMeta(alert)
        holder.binding.tvAlertType.text = localizedType(holder, alert)
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

    // This function replaces the current alert list and refreshes the RecyclerView.
    fun updateItems(newItems: List<AlertItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    // This function builds the date and complaint id text shown below each alert.
    private fun buildMeta(alert: AlertItem): String {
        val dateLabel = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(alert.timestamp))
        return if (alert.complaintDisplayId.isNotBlank()) {
            "${alert.complaintDisplayId} • $dateLabel"
        } else {
            dateLabel
        }
    }

    // This function converts stored alert types into translated labels for the UI.
    private fun localizedType(holder: AlertViewHolder, alert: AlertItem): String {
        val context = holder.binding.root.context
        return when (alert.type) {
            "Complaint Update" -> context.getString(R.string.alert_type_complaint_update)
            "Assignment" -> context.getString(R.string.alert_type_assignment)
            "Broadcast" -> context.getString(R.string.alert_type_broadcast)
            "Resolved" -> context.getString(R.string.status_resolved)
            else -> alert.type
        }
    }

    // This function converts saved internal titles into user-friendly translated titles.
    private fun localizedTitle(holder: AlertViewHolder, alert: AlertItem): String {
        val context = holder.binding.root.context
        return when (alert.title) {
            "Complaint settings saved" -> context.getString(R.string.alert_title_complaint_settings_saved)
            "Officer assigned" -> context.getString(R.string.alert_title_officer_assigned)
            "Broadcast sent" -> context.getString(R.string.alerts_broadcast_sent_title)
            else -> alert.title
        }
    }

    // This function converts saved alert body text into readable translated messages.
    private fun localizedBody(holder: AlertViewHolder, alert: AlertItem): String {
        val context = holder.binding.root.context
        return when {
            alert.body == "Something important settings were updated by admin. ETA was recalculated for the citizen." ->
                context.getString(R.string.alert_body_settings_saved_eta)
            alert.body.startsWith("A field officer was assigned to ") -> {
                val complaintTitle = alert.body
                    .removePrefix("A field officer was assigned to ")
                    .removeSuffix(".")
                context.getString(R.string.alert_body_officer_assigned, complaintTitle)
            }
            alert.title == "Broadcast sent" -> alert.body
            else -> alert.body
        }
    }
}
