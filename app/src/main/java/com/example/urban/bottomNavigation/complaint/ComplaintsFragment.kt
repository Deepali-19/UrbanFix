package com.example.urban.bottomNavigation.complaint

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.urban.R
import com.example.urban.databinding.FragmentComplaintsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ComplaintFragment : Fragment(R.layout.fragment_complaints) {

    companion object {
        private val APP_DEPARTMENTS = listOf(
            "Water",
            "Roads",
            "Sanitation",
            "Electricity"
        )
    }

    private lateinit var binding: FragmentComplaintsBinding
    private val db = FirebaseDatabase.getInstance().reference

    private val allComplaints = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter

    private var currentRole = ""
    private var currentDepartment = ""
    private var currentUid = ""
    private var hasLoadedComplaints = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentComplaintsBinding.bind(view)
        showLoading(true)

        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())

        adapter = ComplaintAdapter(mutableListOf()) { complaint ->
            val complaintKey = complaint.firebaseKey.ifBlank { complaint.complaintId }

            if (complaintKey.isNotBlank()) {
                val fragment = ComplaintDetailFragment.newInstance(complaintKey)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        binding.rvComplaints.adapter = adapter

        setupStaticFilterDropdowns()
        setupFilterListeners()
        loadComplaints()
    }

    private fun setupStaticFilterDropdowns() {
        setupDropdown(
            view = binding.actStatusFilter,
            items = listOf("All Statuses", "Pending", "In Progress", "Resolved"),
            defaultValue = "All Statuses"
        )
        setupDropdown(
            view = binding.actPriorityFilter,
            items = listOf("All Priorities", "High", "Medium", "Low"),
            defaultValue = "All Priorities"
        )
        setupDropdown(
            view = binding.actSortFilter,
            items = listOf("Newest First", "Oldest First", "High Priority First"),
            defaultValue = "Newest First"
        )
        setupDropdown(
            view = binding.actDepartmentFilter,
            items = listOf("All Departments"),
            defaultValue = "All Departments"
        )
    }

    private fun setupFilterListeners() {
        binding.actStatusFilter.setOnItemClickListener { _, _, _, _ -> applyFilters() }
        binding.actPriorityFilter.setOnItemClickListener { _, _, _, _ -> applyFilters() }
        binding.actDepartmentFilter.setOnItemClickListener { _, _, _, _ -> applyFilters() }
        binding.actSortFilter.setOnItemClickListener { _, _, _, _ -> applyFilters() }
    }

    private fun loadComplaints() {
        currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.child("Users").child(currentUid).get().addOnSuccessListener { userSnap ->
            currentRole = userSnap.child("role").value.toString()
            currentDepartment = userSnap.child("department").value.toString()
            adapter.showUnreadIndicator = currentRole != "Field Officer"

            db.child("Complaints")
                .addValueEventListener(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {
                        allComplaints.clear()

                        for (data in snapshot.children) {
                            val complaint = data.getValue(Complaint::class.java) ?: continue
                            complaint.firebaseKey = data.key.orEmpty()

                            if (shouldIncludeComplaint(complaint)) {
                                allComplaints.add(complaint)
                            }
                        }

                        hasLoadedComplaints = true
                        updateDepartmentOptions()
                        applyFilters()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        showLoading(false)
                    }
                })
        }
            .addOnFailureListener {
                showLoading(false)
            }
    }

    private fun shouldIncludeComplaint(complaint: Complaint): Boolean {
        return when (currentRole) {
            "Super Admin" -> true
            "Department Admin" -> {
                ComplaintDataFormatter.resolvedDepartment(complaint) == normalizeDepartment(currentDepartment)
            }
            "Field Officer" -> complaint.allottedOfficerId == currentUid
            else -> false
        }
    }

    private fun updateDepartmentOptions() {
        val selectedDepartment = binding.actDepartmentFilter.text?.toString().orEmpty()
        val options = arrayListOf("All Departments")
        options.addAll(APP_DEPARTMENTS)

        setupDropdown(binding.actDepartmentFilter, options, "All Departments")

        if (selectedDepartment in options) {
            binding.actDepartmentFilter.setText(selectedDepartment, false)
        }
    }

    private fun applyFilters() {
        val selectedStatus = binding.actStatusFilter.text?.toString().orEmpty()
        val selectedPriority = binding.actPriorityFilter.text?.toString().orEmpty()
        val selectedDepartment = binding.actDepartmentFilter.text?.toString().orEmpty()
        val selectedSort = binding.actSortFilter.text?.toString().orEmpty()

        val filteredComplaints = allComplaints
            .filter { complaint ->
                matchesStatus(complaint, selectedStatus) &&
                    matchesPriority(complaint, selectedPriority) &&
                    matchesDepartment(complaint, selectedDepartment)
            }
            .sortedWith(sortComparator(selectedSort))

        adapter.updateList(filteredComplaints)
        updateSummary(filteredComplaints)
    }

    private fun matchesStatus(complaint: Complaint, selectedStatus: String): Boolean {
        return when (selectedStatus) {
            "Pending" -> complaint.status == 0
            "In Progress" -> complaint.status == 1
            "Resolved" -> complaint.status == 2
            else -> true
        }
    }

    private fun matchesPriority(complaint: Complaint, selectedPriority: String): Boolean {
        return when (selectedPriority) {
            "High" -> complaint.priority == 2
            "Medium" -> complaint.priority == 1
            "Low" -> complaint.priority == 0
            else -> true
        }
    }

    private fun matchesDepartment(complaint: Complaint, selectedDepartment: String): Boolean {
        if (selectedDepartment.isBlank() || selectedDepartment == "All Departments") {
            return true
        }

        return ComplaintDataFormatter.resolvedDepartment(complaint) == normalizeDepartment(selectedDepartment)
    }

    private fun sortComparator(selectedSort: String): Comparator<Complaint> {
        return when (selectedSort) {
            "Oldest First" -> compareBy { it.timestamp }
            "High Priority First" -> compareByDescending<Complaint> { it.priority }
                .thenByDescending { it.timestamp }
            else -> compareByDescending { it.timestamp }
        }
    }

    private fun normalizeDepartment(value: String): String {
        return when (value.trim().lowercase()) {
            "water" -> "Water"
            "road", "roads" -> "Roads"
            "sanitation", "sanitisation" -> "Sanitation"
            "electricity" -> "Electricity"
            else -> value.trim()
        }
    }

    private fun updateSummary(complaints: List<Complaint>) {
        val pending = complaints.count { it.status == 0 }
        val progress = complaints.count { it.status == 1 }
        val resolved = complaints.count { it.status == 2 }

        showLoading(false)
        binding.tvTotal.text = "Total: ${complaints.size}"
        binding.tvPending.text = "Pending: $pending"
        binding.tvProgress.text = "In Progress: $progress"
        binding.tvResolved.text = "Resolved: $resolved"
        binding.tvEmptyState.visibility = if (hasLoadedComplaints && complaints.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.complaintsLoadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.complaintsContentContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun setupDropdown(
        view: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        items: List<String>,
        defaultValue: String
    ) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            items
        )

        view.setAdapter(adapter)
        view.keyListener = null
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                view.showDropDown()
            }
        }

        if (view.text.isNullOrBlank()) {
            view.setText(defaultValue, false)
        } else {
            val currentValue = view.text.toString()
            if (currentValue !in items) {
                view.setText(defaultValue, false)
            }
        }
    }
}
