package com.example.urban.bottomNavigation.drawer.DA

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.databinding.ItemFieldOfficerBinding

class DepartmentAdminAdapter(
    private val items: ArrayList<DepartmentAdminProfile>,
    private val onClick: (DepartmentAdminProfile) -> Unit
) : RecyclerView.Adapter<DepartmentAdminAdapter.DepartmentAdminViewHolder>() {

    // This holder keeps one department admin card ready for quick binding.
    inner class DepartmentAdminViewHolder(
        val binding: ItemFieldOfficerBinding
    ) : RecyclerView.ViewHolder(binding.root)

    // This creates one card row for the RecyclerView.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentAdminViewHolder {
        val binding = ItemFieldOfficerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DepartmentAdminViewHolder(binding)
    }

    // This tells the list how many department admin cards are available.
    override fun getItemCount(): Int = items.size

    // This fills the card with department admin details from the same city.
    override fun onBindViewHolder(holder: DepartmentAdminViewHolder, position: Int) {
        val admin = items[position]

        holder.binding.tvName.text = admin.name.ifBlank { "Department Admin" }
        holder.binding.tvDepartment.text = "Dept: ${admin.department.ifBlank { "General" }}"
        holder.binding.tvPhone.text = when {
            admin.employeeId.isNotBlank() -> "Employee ID: ${admin.employeeId}"
            admin.email.isNotBlank() -> admin.email
            else -> "Details not available"
        }
        holder.binding.tvCount.text = "City: ${admin.city.ifBlank { "Not added" }}"

        if (admin.profileImageUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(admin.profileImageUrl)
                .placeholder(R.drawable.users)
                .error(R.drawable.users)
                .into(holder.binding.imgProfile)
        } else {
            holder.binding.imgProfile.setImageResource(R.drawable.users)
        }

        holder.itemView.setOnClickListener {
            onClick(admin)
        }
    }

    // This swaps the visible list when Firebase data changes.
    fun updateItems(newItems: List<DepartmentAdminProfile>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
