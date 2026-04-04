package com.example.urban.bottomNavigation.drawer.FO

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class FieldOfficerAdapter (
    private val list: ArrayList<FieldOfficer>,
    private val onClick: (FieldOfficer) -> Unit) :
    RecyclerView.Adapter<FieldOfficerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val img = view.findViewById<CircleImageView>(R.id.imgProfile)
            val name = view.findViewById<TextView>(R.id.tvName)
            val dept = view.findViewById<TextView>(R.id.tvDepartment)
            val phone = view.findViewById<TextView>(R.id.tvPhone)
            val count = view.findViewById<TextView>(R.id.tvCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_field_officer, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            val officer = list[position]

            holder.name.text = officer.name
            holder.dept.text = "Dept: ${officer.department}"
            holder.phone.text = "Phone: ${officer.phone}"

            if (officer.profileImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(officer.profileImageUrl)
                    .into(holder.img)
            }

            holder.itemView.setOnClickListener {
                onClick(officer)
            }
        }
}