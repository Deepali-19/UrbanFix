package com.example.urban.bottomNavigation.complaint

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.urban.R
import com.example.urban.databinding.FragmentComplaintsBinding
import com.google.firebase.database.*

class ComplaintFragment : Fragment(R.layout.fragment_complaints) {

    private lateinit var binding: FragmentComplaintsBinding
    private val db = FirebaseDatabase.getInstance().reference

    private val complaintList = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding = FragmentComplaintsBinding.bind(view)

        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())

        adapter = ComplaintAdapter(complaintList) {

            // Navigate to detail screen
            val fragment = ComplaintDetailFragment.newInstance(it.complaintId)

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.rvComplaints.adapter = adapter

        loadComplaints()
    }

    private fun loadComplaints() {

        val uid = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.uid ?: return

        db.child("Users").child(uid).get().addOnSuccessListener { userSnap ->

            val role = userSnap.child("role").value.toString()
            val department = userSnap.child("department").value.toString()

            db.child("Complaints")
                .addValueEventListener(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        complaintList.clear()

                        var pending = 0
                        var progress = 0
                        var resolved = 0

                        for (data in snapshot.children) {

                            val complaint =
                                data.getValue(Complaint::class.java) ?: continue

                            var shouldAdd = false

                            when (role) {

                                "Super Admin" -> {
                                    shouldAdd = true
                                }

                                "Department Admin" -> {
                                    if (complaint.departmentId == department) {
                                        shouldAdd = true
                                    }
                                }

                                "Field Officer" -> {
                                    if (complaint.allottedOfficerId == uid) {
                                        shouldAdd = true
                                    }
                                }
                            }

                            if (shouldAdd) {

                                complaintList.add(complaint)

                                when (complaint.status) {
                                    0 -> pending++
                                    1 -> progress++
                                    2 -> resolved++
                                }
                            }
                        }

                        complaintList.sortByDescending { it.timestamp }

                        adapter.notifyDataSetChanged()

                        binding.tvTotal.text = "Total: ${complaintList.size}"
                        binding.tvPending.text = "Pending: $pending"
                        binding.tvProgress.text = "In Progress: $progress"
                        binding.tvResolved.text = "Resolved: $resolved"
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

//    private fun loadComplaints() {
//
//        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
//
//        db.child("Users").child(uid).get().addOnSuccessListener { userSnap ->
//
//            val role = userSnap.child("role").value.toString()
//            val department = userSnap.child("department").value.toString()
//
//            db.child("complaints")
//                .addValueEventListener(object : ValueEventListener {
//
//                    override fun onDataChange(snapshot: DataSnapshot) {
//
//                        complaintList.clear()
//
//                        var pending = 0
//                        var progress = 0
//                        var resolved = 0
//
//                        for (data in snapshot.children) {
//
//                            val complaint =
//                                data.getValue(Complaint::class.java) ?: continue
//
//                            when (role) {
//
//                                "Super Admin" -> {
//                                    complaintList.add(complaint)
//                                }
//
//                                "Department Admin" -> {
//                                    if (complaint.departmentId == department) {
//                                        complaintList.add(complaint)
//                                    }
//                                }
//
//                                "Field Officer" -> {
//                                    if (complaint.allottedOfficerId == uid) {
//                                        complaintList.add(complaint)
//                                    }
//                                }
//                            }
//
//                            when (complaint.status) {
//                                0 -> pending++
//                                1 -> progress++
//                                2 -> resolved++
//                            }
//                        }
//
//                        complaintList.sortByDescending { it.timestamp }
//
//                        adapter.notifyDataSetChanged()
//
//                        binding.tvTotal.text = "Total: ${complaintList.size}"
//                        binding.tvPending.text = "Pending: $pending"
//                        binding.tvProgress.text = "In Progress: $progress"
//                        binding.tvResolved.text = "Resolved: $resolved"
//                    }
//
//                    override fun onCancelled(error: DatabaseError) {}
//                })
//        }
//    }
}
//    private fun loadComplaints() {
//
//        db.child("complaints")
//            .addValueEventListener(object : ValueEventListener {
//
//                override fun onDataChange(snapshot: DataSnapshot) {
//
//                    complaintList.clear()
//
//                    var pending = 0
//                    var progress = 0
//                    var resolved = 0
//
//                    for (data in snapshot.children) {
//
//                        val complaint =
//                            data.getValue(Complaint::class.java) ?: continue
//
//                        complaintList.add(complaint)
//
//                        when (complaint.status) {
//                            0 -> pending++
//                            1 -> progress++
//                            2 -> resolved++
//                        }
//                    }
//
//                    // Sort complaints by newest first
//                    complaintList.sortByDescending { it.timestamp }
//
//                    adapter.notifyDataSetChanged()
//
//                    binding.tvTotal.text = "Total: ${complaintList.size}"
//                    binding.tvPending.text = "Pending: $pending"
//                    binding.tvProgress.text = "In Progress: $progress"
//                    binding.tvResolved.text = "Resolved: $resolved"
//                }
//
//                override fun onCancelled(error: DatabaseError) {}
//            })
//    }
//}