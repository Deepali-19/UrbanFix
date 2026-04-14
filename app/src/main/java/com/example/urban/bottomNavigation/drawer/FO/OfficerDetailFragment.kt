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
import com.example.urban.bottomNavigation.complaint.ComplaintDetailFragment
import com.example.urban.bottomNavigation.complaint.ComplaintSnapshotParser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView


class OfficerDetailFragment : Fragment(R.layout.fragment_officer_detail) {

    private lateinit var loadingContainer: View
    private lateinit var emptyView: View
    private lateinit var tvName: TextView
    private lateinit var tvDept: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvOfficerComplaintSummary: TextView
    private lateinit var imgProfile: CircleImageView
    private lateinit var recyclerView: RecyclerView

    private val database = FirebaseDatabase.getInstance().reference

    private var officerId: String? = null
    private val complaintList = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter
    private var isOfficerLoaded = false
    private var isComplaintsLoaded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadingContainer = view.findViewById(R.id.officerDetailLoadingContainer)
        emptyView = view.findViewById(R.id.tvOfficerComplaintEmpty)
        tvName = view.findViewById(R.id.tvName)
        tvDept = view.findViewById(R.id.tvDept)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvOfficerComplaintSummary = view.findViewById(R.id.tvOfficerComplaintSummary)
        imgProfile = view.findViewById(R.id.imgProfile)
        recyclerView = view.findViewById(R.id.recyclerComplaints)

        officerId = arguments?.getString("officerId")
        showLoading(true)

        adapter = ComplaintAdapter(mutableListOf()) { complaint ->
            val complaintKey = complaint.firebaseKey.ifBlank { complaint.complaintId }
            if (complaintKey.isNotBlank()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ComplaintDetailFragment.newInstance(complaintKey))
                    .addToBackStack(null)
                    .commit()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadOfficerDetails()
        loadComplaints()
    }

    private fun loadOfficerDetails() {
        val targetOfficerId = officerId ?: return
        database.child("Users").child(targetOfficerId)
            .get()
            .addOnSuccessListener {

                val name = it.child("name").value.toString()
                val dept = it.child("department").value.toString()
                val phone = it.child("phone").value.toString()
                val employeeId = it.child("employeeId").value.toString()
                val city = it.child("city").value.toString()
                val image = it.child("profileImageUrl").value?.toString()
                val metaText = when {
                    phone.isNotBlank() -> "Phone: $phone"
                    employeeId.isNotBlank() -> "Employee ID: $employeeId"
                    city.isNotBlank() -> "City: $city"
                    else -> "Details not available"
                }

                tvName.text = name
                tvDept.text = "Department: $dept"
                tvPhone.text = metaText
                isOfficerLoaded = true
                updateLoadingState()

                if (!image.isNullOrEmpty()) {
                    Glide.with(requireContext()).load(image).into(imgProfile)
                } else {
                    imgProfile.setImageResource(R.drawable.users)
                }
            }
            .addOnFailureListener {
                isOfficerLoaded = true
                updateLoadingState()
            }
    }

    private fun loadComplaints() {

        database.child("Complaints")
            .orderByChild("allottedOfficerId")
            .equalTo(officerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    complaintList.clear()

                    for (data in snapshot.children) {

                        val complaint = ComplaintSnapshotParser.fromSnapshot(data) ?: continue

                        complaintList.add(complaint)
                    }

                    complaintList.sortByDescending { it.timestamp }
                    adapter.updateList(complaintList)
                    updateComplaintSummary()
                    isComplaintsLoaded = true
                    updateLoadingState()
                }

                override fun onCancelled(error: DatabaseError) {
                    updateComplaintSummary()
                    isComplaintsLoaded = true
                    updateLoadingState()
                }
            })
    }

    private fun updateComplaintSummary() {
        val assignedCount = complaintList.size
        val activeCount = complaintList.count { it.status == 1 }
        val resolvedCount = complaintList.count { it.status == 2 }
        tvOfficerComplaintSummary.text =
            "Assigned $assignedCount • Active $activeCount • Resolved $resolvedCount"
        emptyView.visibility = if (assignedCount == 0) View.VISIBLE else View.GONE
    }

    private fun updateLoadingState() {
        showLoading(!(isOfficerLoaded && isComplaintsLoaded))
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        if (isLoading) {
            emptyView.visibility = View.GONE
        }
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
