package com.example.urban.bottomNavigation.drawer.FO

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintAdapter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView


class OfficerDetailFragment : Fragment(R.layout.fragment_officer_detail) {

    private lateinit var tvName: TextView
    private lateinit var tvDept: TextView
    private lateinit var tvPhone: TextView
    private lateinit var imgProfile: CircleImageView
    private lateinit var recyclerView: RecyclerView

    private val database = FirebaseDatabase.getInstance().reference

    private var officerId: String? = null
    private val complaintList = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        tvName = view.findViewById(R.id.tvName)
        tvDept = view.findViewById(R.id.tvDept)
        tvPhone = view.findViewById(R.id.tvPhone)
        imgProfile = view.findViewById(R.id.imgProfile)
        recyclerView = view.findViewById(R.id.recyclerComplaints)

        officerId = arguments?.getString("officerId")

        adapter = ComplaintAdapter(complaintList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadOfficerDetails()
        loadComplaints()
    }

    private fun loadOfficerDetails() {

        database.child("Users").child(officerId!!)
            .get()
            .addOnSuccessListener {

                val name = it.child("name").value.toString()
                val dept = it.child("department").value.toString()
                val phone = it.child("phone").value.toString()
                val image = it.child("profileImageUrl").value?.toString()

                tvName.text = name
                tvDept.text = "Department: $dept"
                tvPhone.text = "Phone: $phone"

                if (!image.isNullOrEmpty()) {
                    Glide.with(requireContext()).load(image).into(imgProfile)
                }
            }
    }

    private fun loadComplaints() {

        database.child("complaints")
            .orderByChild("allottedOfficerId")
            .equalTo(officerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    complaintList.clear()

                    for (data in snapshot.children) {

                        val complaint =
                            data.getValue(Complaint::class.java) ?: continue

                        complaintList.add(complaint)
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    companion object {

        fun newInstance(id: String): OfficerDetailFragment {

            val fragment = OfficerDetailFragment()
            val bundle = Bundle()
            bundle.putString("officerId", id)
            fragment.arguments = bundle
            return fragment
        }
    }
}