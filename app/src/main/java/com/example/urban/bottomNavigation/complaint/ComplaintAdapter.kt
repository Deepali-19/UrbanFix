package com.example.urban.bottomNavigation.complaint

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.databinding.ItemComplaintBinding
import java.text.SimpleDateFormat
import java.util.*

class ComplaintAdapter(
    private var list: MutableList<Complaint> = mutableListOf(),
    var showUnreadIndicator: Boolean = false,
    private val click: ((Complaint) -> Unit)? = null
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemComplaintBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val binding = ItemComplaintBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val complaint = list[position]
        val displayId = complaint.complaintId.ifBlank {
            complaint.firebaseKey.ifBlank { "No ID" }
        }
        val locationText = complaint.location.ifBlank { "Location not available" }
        val departmentText = ComplaintDataFormatter.resolvedDepartment(complaint)
        val dateText = if (complaint.timestamp > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(complaint.timestamp))
        } else {
            "No date"
        }

        holder.binding.tvComplaintId.text = displayId
        holder.binding.viewUnreadDot.visibility =
            if (showUnreadIndicator && !complaint.readByAdmin) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        holder.binding.tvTitle.text = complaint.title.ifBlank { "Untitled complaint" }
        holder.binding.tvLocation.text = locationText
        holder.binding.tvDepartment.text = departmentText

        holder.binding.tvAssigned.text =
            if (complaint.allottedOfficerId.isEmpty())
                "Not assigned yet"
            else
                "Assigned to field officer"
        holder.binding.tvDateTime.text = dateText

        when (complaint.priority) {
            2 -> {
                holder.binding.tvPriority.text = "High"
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_high)
            }

            1 -> {
                holder.binding.tvPriority.text = "Medium"
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_medium)
            }

            else -> {
                holder.binding.tvPriority.text = "Low"
                holder.binding.tvPriority.setBackgroundResource(R.drawable.priority_low)
            }
        }

        when (complaint.status) {
            0 -> {
                holder.binding.tvStatus.text = "Pending"
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_pending)
            }

            1 -> {
                holder.binding.tvStatus.text = "In Progress"
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_progress)
            }

            2 -> {
                holder.binding.tvStatus.text = "Resolved"
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_resolved)
            }
        }

        holder.itemView.setOnClickListener {
            click?.invoke(complaint)
        }
    }

    fun updateList(newList: List<Complaint>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}





//----version 1 -------------
//package com.example.urban.bottomNavigation.complaint
//
//import android.view.LayoutInflater
//import android.view.ViewGroup
//import androidx.recyclerview.widget.RecyclerView
//import com.example.urban.R
//import com.example.urban.databinding.ItemComplaintBinding
//
//class ComplaintAdapter(
//    private val list: List<Complaint>,
//    private val click: (Complaint) -> Unit
//) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {
//
//    inner class ViewHolder(val binding: ItemComplaintBinding) :
//        RecyclerView.ViewHolder(binding.root)
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//
//        val binding = ItemComplaintBinding.inflate(
//            LayoutInflater.from(parent.context),
//            parent,
//            false
//        )
//
//        return ViewHolder(binding)
//    }
//
//    override fun getItemCount(): Int = list.size
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//
//        val complaint = list[position]
//
//        holder.binding.tvComplaintId.text =
//            complaint.complaintId ?: "No ID"
//        holder.binding.tvTitle.text = complaint.title
//        holder.binding.tvLocation.text = complaint.location
//        holder.binding.tvDepartment.text = complaint.issueType
//
//        // DATE FORMAT
//        val date = java.util.Date(complaint.timestamp)
//        val format = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
//        holder.binding.tvDateTime.text = format.format(date)
//
//        // Assigned officer
//        if (complaint.allottedOfficerId.isEmpty()) {
//            holder.binding.tvAssigned.text = "Assigned: Not Assigned"
//        } else {
//            holder.binding.tvAssigned.text = "Assigned: Officer"
//        }
//
//        when (complaint.status) {
//
//            0 -> {
//                holder.binding.tvStatus.text = "Pending"
//                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_pending)
//            }
//
//            1 -> {
//                holder.binding.tvStatus.text = "In Progress"
//                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_progress)
//            }
//
//            2 -> {
//                holder.binding.tvStatus.text = "Resolved"
//                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_resolved)
//            }
//        }
//
//        holder.itemView.setOnClickListener {
//            click(complaint)
//        }
//    }
//}







//--------------------old-----------------------------------------------
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//
//        val complaint = list[position]
//
//        // Complaint basic info
//        holder.binding.tvComplaintId.text = complaint.complaintId
//        holder.binding.tvTitle.text = complaint.title
//        holder.binding.tvLocation.text = complaint.location
//        holder.binding.tvDepartment.text = complaint.issueType
//
//        // Assigned Officer
//        holder.binding.tvAssigned.text =
//            if (complaint.allottedOfficerId.isEmpty())
//                "Assigned: Not Assigned"
//            else
//                "Assigned: ${complaint.allottedOfficerId}"
//
//        // Complaint Status UI
//        when (complaint.status) {
//
//            0 -> {
//                holder.binding.tvStatus.text = "Pending"
//                holder.binding.tvStatus.setBackgroundResource(
//                    R.drawable.status_pending
//                )
//            }
//
//            1 -> {
//                holder.binding.tvStatus.text = "In Progress"
//                holder.binding.tvStatus.setBackgroundResource(
//                    R.drawable.status_progress
//                )
//            }
//
//            2 -> {
//                holder.binding.tvStatus.text = "Resolved"
//                holder.binding.tvStatus.setBackgroundResource(
//                    R.drawable.status_resolved
//                )
//            }
//        }
//
//        // Click listener
//        holder.itemView.setOnClickListener {
//            click(complaint)
//        }
//    }
