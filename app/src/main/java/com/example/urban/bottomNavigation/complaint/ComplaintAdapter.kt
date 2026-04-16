package com.example.urban.bottomNavigation.complaint

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.databinding.ItemComplaintBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComplaintAdapter(
    private var list: MutableList<Complaint> = mutableListOf(),
    var showUnreadIndicator: Boolean = false,
    private val click: ((Complaint) -> Unit)? = null
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    // This holds the view binding for one complaint row.
    inner class ViewHolder(val binding: ItemComplaintBinding) :
        RecyclerView.ViewHolder(binding.root)

    // This creates one complaint row view for the RecyclerView.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComplaintBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    // This returns the total number of complaint items in the list.
    override fun getItemCount(): Int = list.size

    // This fills each complaint row with the latest complaint values.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val complaint = list[position]
        val context = holder.binding.root.context

        val displayId = complaint.complaintId.ifBlank {
            complaint.firebaseKey.ifBlank { context.getString(R.string.complaint_no_id) }
        }

        val dateText = if (complaint.timestamp > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(complaint.timestamp))
        } else {
            context.getString(R.string.complaint_no_date)
        }

        val locationText = ComplaintDataFormatter.localizedLocationLabel(context, complaint)
        val coordinatesText = ComplaintDataFormatter.localizedCoordinatesLabel(context, complaint)
        val departmentText = ComplaintDataFormatter.localizedDepartmentName(
            context,
            ComplaintDataFormatter.resolvedDepartment(complaint)
        )

        bindBasicInfo(holder, complaint, displayId, dateText, locationText, coordinatesText, departmentText)
        bindPriority(holder, complaint.priority)
        bindStatus(holder, complaint.status)

        holder.itemView.setOnClickListener {
            click?.invoke(complaint)
        }
    }

    // This binds the complaint title, date, assignment, and other text fields.
    private fun bindBasicInfo(
        holder: ViewHolder,
        complaint: Complaint,
        displayId: String,
        dateText: String,
        locationText: String,
        coordinatesText: String,
        departmentText: String
    ) {
        val context = holder.binding.root.context

        holder.binding.tvComplaintId.text = displayId
        holder.binding.tvTitle.text = complaint.title.ifBlank {
            context.getString(R.string.complaint_untitled)
        }
        holder.binding.tvLocation.text = locationText
        holder.binding.tvCoordinates.text = coordinatesText
        holder.binding.tvDepartment.text = departmentText
        holder.binding.tvDateTime.text = dateText
        holder.binding.tvEta.text = ComplaintEtaManager.etaShortLabel(complaint)
        holder.binding.tvAssigned.text =
            if (complaint.allottedOfficerId.isBlank()) {
                context.getString(R.string.complaint_not_assigned)
            } else {
                context.getString(R.string.complaint_assigned_to_officer)
            }

        holder.binding.tvCoordinates.visibility =
            if (ComplaintDataFormatter.hasCoordinates(complaint)) View.VISIBLE else View.GONE

        holder.binding.viewUnreadDot.visibility =
            if (showUnreadIndicator && !complaint.readByAdmin) View.VISIBLE else View.GONE
    }

    // This shows the correct priority text and background color.
    private fun bindPriority(holder: ViewHolder, priority: Int) {
        val context = holder.binding.root.context

        when (priority) {
            2 -> {
                holder.binding.tvPriority.text = context.getString(R.string.priority_high)
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_high)
            }

            1 -> {
                holder.binding.tvPriority.text = context.getString(R.string.priority_medium)
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_medium)
            }

            else -> {
                holder.binding.tvPriority.text = context.getString(R.string.priority_low)
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_low)
            }
        }
    }

    // This shows the correct status text and background color.
    private fun bindStatus(holder: ViewHolder, status: Int) {
        val context = holder.binding.root.context

        when (status) {
            0 -> {
                holder.binding.tvStatus.text = context.getString(R.string.status_pending)
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_pending)
            }

            1 -> {
                holder.binding.tvStatus.text = context.getString(R.string.status_in_progress)
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_progress)
            }

            else -> {
                holder.binding.tvStatus.text = context.getString(R.string.status_resolved)
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_resolved)
            }
        }
    }

    // This replaces the current RecyclerView list with the latest complaint data.
    fun updateList(newList: List<Complaint>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}
