package com.example.urban.bottomNavigation.approvals

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.loginSingUp.ApprovalRequest
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// This adapter shows pending account approval requests for the city super admin.
class ApprovalRequestAdapter(
    private val onViewId: (ApprovalRequest) -> Unit,
    private val onAccept: (ApprovalRequest) -> Unit,
    private val onReject: (ApprovalRequest) -> Unit
) : RecyclerView.Adapter<ApprovalRequestAdapter.ApprovalViewHolder>() {

    private val items = mutableListOf<ApprovalRequest>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApprovalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_approval_request, parent, false)
        return ApprovalViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApprovalViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name.ifBlank { "Unnamed User" }
        holder.tvEmail.text = item.email
        holder.tvMeta.text = buildString {
            append(item.role.ifBlank { "Official" })
            append(" • ")
            append(item.city.ifBlank { "No city" })
            if (item.department.isNotBlank() && item.department != "All Departments") {
                append(" • ")
                append(item.department)
            }
        }
        holder.tvSubmitted.text = holder.itemView.context.getString(
            R.string.approval_submitted_on,
            dateFormat.format(Date(item.submittedAt))
        )

        holder.btnViewId.visibility = if (item.idProofUrl.isBlank()) View.GONE else View.VISIBLE
        holder.btnViewId.setOnClickListener { onViewId(item) }
        holder.btnAccept.setOnClickListener { onAccept(item) }
        holder.btnReject.setOnClickListener { onReject(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ApprovalRequest>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ApprovalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvApprovalName)
        val tvEmail: TextView = view.findViewById(R.id.tvApprovalEmail)
        val tvMeta: TextView = view.findViewById(R.id.tvApprovalMeta)
        val tvSubmitted: TextView = view.findViewById(R.id.tvApprovalSubmitted)
        val btnViewId: MaterialButton = view.findViewById(R.id.btnApprovalViewId)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnApprovalAccept)
        val btnReject: MaterialButton = view.findViewById(R.id.btnApprovalReject)
    }
}
