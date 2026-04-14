package com.example.urban.bottomNavigation.complaint

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
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
        const val REQUEST_KEY_FILTERS = "dashboardComplaintFilters"
        private const val KEY_STATUS = "status"
        private const val KEY_PRIORITY = "priority"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_SORT = "sort"
        private const val KEY_RANGE = "range"
        private const val KEY_COMPLAINT_KEYS = "complaint_keys"

        private val APP_DEPARTMENTS = listOf(
            "Water",
            "Roads",
            "Sanitation",
            "Electricity"
        )

        fun filterBundle(
            status: String? = null,
            priority: String? = null,
            department: String? = null,
            sort: String? = null,
            range: String? = null,
            complaintKeys: ArrayList<String>? = null
        ): Bundle {
            return bundleOf(
                KEY_STATUS to status,
                KEY_PRIORITY to priority,
                KEY_DEPARTMENT to department,
                KEY_SORT to sort,
                KEY_RANGE to range,
                KEY_COMPLAINT_KEYS to complaintKeys
            )
        }
    }

    private lateinit var binding: FragmentComplaintsBinding
    private val db = FirebaseDatabase.getInstance().reference

    private val allComplaints = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter

    private var currentRole = ""
    private var currentDepartment = ""
    private var currentUid = ""
    private var hasLoadedComplaints = false
    private var pendingStatusFilter: String? = null
    private var pendingPriorityFilter: String? = null
    private var pendingDepartmentFilter: String? = null
    private var pendingSortFilter: String? = null
    private var pendingRangeFilter: String? = null
    private var pendingComplaintKeys: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_FILTERS,
            this
        ) { _, bundle ->
            pendingStatusFilter = bundle.getString(KEY_STATUS)
            pendingPriorityFilter = bundle.getString(KEY_PRIORITY)
            pendingDepartmentFilter = bundle.getString(KEY_DEPARTMENT)
            pendingSortFilter = bundle.getString(KEY_SORT)
            pendingRangeFilter = bundle.getString(KEY_RANGE)
            pendingComplaintKeys = bundle.getStringArrayList(KEY_COMPLAINT_KEYS)
            applyPendingDashboardFilters()
        }
    }

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
        applyPendingDashboardFilters()
        setupFilterListeners()
        loadComplaints()
    }

    private fun setupStaticFilterDropdowns() {
        setupDropdown(
            view = binding.actStatusFilter,
            items = statusOptions(),
            defaultValue = getString(R.string.filter_all_statuses)
        )
        setupDropdown(
            view = binding.actPriorityFilter,
            items = priorityOptions(),
            defaultValue = getString(R.string.filter_all_priorities)
        )
        setupDropdown(
            view = binding.actSortFilter,
            items = sortOptions(),
            defaultValue = getString(R.string.sort_newest_first)
        )
        setupDropdown(
            view = binding.actDepartmentFilter,
            items = listOf(getString(R.string.department_all)),
            defaultValue = getString(R.string.department_all)
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
                            val complaint = ComplaintSnapshotParser.fromSnapshot(data) ?: continue

                            if (shouldIncludeComplaint(complaint)) {
                                allComplaints.add(complaint)
                            }
                        }

                        hasLoadedComplaints = true
                        syncEtaForVisibleComplaints()
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

    private fun syncEtaForVisibleComplaints() {
        if (currentRole == "Field Officer") return

        val etaUpdates = ComplaintEtaManager.buildEtaUpdates(allComplaints)
        etaUpdates.forEach { (complaintKey, updates) ->
            db.child("Complaints").child(complaintKey).updateChildren(updates)
        }
    }

    private fun updateDepartmentOptions() {
        val selectedDepartment = binding.actDepartmentFilter.text?.toString().orEmpty()
        val options = arrayListOf(getString(R.string.department_all))
        options.addAll(APP_DEPARTMENTS.map { ComplaintDataFormatter.localizedDepartmentName(requireContext(), it) })

        setupDropdown(binding.actDepartmentFilter, options, getString(R.string.department_all))

        pendingDepartmentFilter?.let { requestedDepartment ->
            val normalizedRequestedDepartment = ComplaintDataFormatter.localizedDepartmentName(
                requireContext(),
                normalizeDepartment(requestedDepartment)
            )
            if (normalizedRequestedDepartment in options) {
                binding.actDepartmentFilter.setText(normalizedRequestedDepartment, false)
                pendingDepartmentFilter = null
                return
            }
        }

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
                        matchesDepartment(complaint, selectedDepartment) &&
                        matchesComplaintSelection(complaint, pendingComplaintKeys) &&
                        matchesDashboardRange(complaint, pendingRangeFilter)
            }
            .sortedWith(sortComparator(selectedSort))

        adapter.updateList(filteredComplaints)
        updateSummary(filteredComplaints)
    }

    private fun matchesStatus(complaint: Complaint, selectedStatus: String): Boolean {
        return when (selectedStatus) {
            getString(R.string.status_pending) -> complaint.status == 0
            getString(R.string.status_in_progress) -> complaint.status == 1
            getString(R.string.status_resolved) -> complaint.status == 2
            else -> true
        }
    }

    private fun matchesPriority(complaint: Complaint, selectedPriority: String): Boolean {
        return when (selectedPriority) {
            getString(R.string.priority_high) -> complaint.priority == 2
            getString(R.string.priority_medium) -> complaint.priority == 1
            getString(R.string.priority_low) -> complaint.priority == 0
            else -> true
        }
    }

    private fun matchesDepartment(complaint: Complaint, selectedDepartment: String): Boolean {
        if (selectedDepartment.isBlank() || selectedDepartment == getString(R.string.department_all)) {
            return true
        }

        return ComplaintDataFormatter.resolvedDepartment(complaint) == normalizeDepartment(selectedDepartment)
    }

    private fun sortComparator(selectedSort: String): Comparator<Complaint> {
        return when (selectedSort) {
            getString(R.string.sort_oldest_first) -> compareBy { it.timestamp }
            getString(R.string.sort_high_priority_first) -> compareByDescending<Complaint> { it.priority }
                .thenByDescending { it.timestamp }
            else -> compareByDescending { it.timestamp }
        }
    }

    private fun normalizeDepartment(value: String): String {
        return when (value.trim().lowercase()) {
            getString(R.string.department_all).lowercase() -> "All Departments"
            getString(R.string.department_water).lowercase() -> "Water"
            getString(R.string.department_roads).lowercase() -> "Roads"
            getString(R.string.department_sanitation).lowercase() -> "Sanitation"
            getString(R.string.department_electricity).lowercase() -> "Electricity"
            "water" -> "Water"
            "road", "roads" -> "Roads"
            "sanitation", "sanitisation" -> "Sanitation"
            "electricity" -> "Electricity"
            else -> value.trim()
        }
    }

    // Map drill-down can pass a hidden set of complaint keys so the list opens on one hotspot only.
    private fun matchesComplaintSelection(
        complaint: Complaint,
        requestedComplaintKeys: ArrayList<String>?
    ): Boolean {
        if (requestedComplaintKeys.isNullOrEmpty()) return true

        val complaintKey = complaint.firebaseKey.ifBlank { complaint.complaintId }
        return complaintKey in requestedComplaintKeys
    }

    // Dashboard can pass a hidden time window so drill-down stays scoped.
    private fun matchesDashboardRange(complaint: Complaint, requestedRange: String?): Boolean {
        val timestamp = complaint.timestamp
        if (requestedRange.isNullOrBlank() || timestamp <= 0L) {
            return true
        }

        val now = java.time.LocalDate.now()
        val complaintDate = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()

        return when (requestedRange) {
            "Today" -> complaintDate == now
            "7 Days" -> !complaintDate.isBefore(now.minusDays(6))
            "30 Days" -> !complaintDate.isBefore(now.minusDays(29))
            "This Month" -> java.time.YearMonth.from(complaintDate) == java.time.YearMonth.now()
            else -> true
        }
    }

    private fun applyPendingDashboardFilters() {
        if (!this::binding.isInitialized) return

        pendingStatusFilter?.let {
            binding.actStatusFilter.setText(localizedStatusOption(it), false)
            pendingStatusFilter = null
        }

        pendingPriorityFilter?.let {
            binding.actPriorityFilter.setText(localizedPriorityOption(it), false)
            pendingPriorityFilter = null
        }

        pendingDepartmentFilter?.let {
            val normalizedDepartment = normalizeDepartment(it)
            binding.actDepartmentFilter.setText(normalizedDepartment, false)
            pendingDepartmentFilter = null
        }

        pendingSortFilter?.let {
            binding.actSortFilter.setText(localizedSortOption(it), false)
            pendingSortFilter = null
        }

        if (pendingRangeFilter.isNullOrBlank()) {
            pendingRangeFilter = null
        }

        if (hasLoadedComplaints) {
            applyFilters()
        }
    }

    private fun updateSummary(complaints: List<Complaint>) {
        val pending = complaints.count { it.status == 0 }
        val progress = complaints.count { it.status == 1 }
        val resolved = complaints.count { it.status == 2 }

        showLoading(false)
        binding.tvTotal.text = getString(R.string.summary_total, complaints.size)
        binding.tvPending.text = getString(R.string.summary_pending, pending)
        binding.tvProgress.text = getString(R.string.summary_progress, progress)
        binding.tvResolved.text = getString(R.string.summary_resolved, resolved)
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

    private fun statusOptions(): List<String> = listOf(
        getString(R.string.filter_all_statuses),
        getString(R.string.status_pending),
        getString(R.string.status_in_progress),
        getString(R.string.status_resolved)
    )

    private fun priorityOptions(): List<String> = listOf(
        getString(R.string.filter_all_priorities),
        getString(R.string.priority_high),
        getString(R.string.priority_medium),
        getString(R.string.priority_low)
    )

    private fun sortOptions(): List<String> = listOf(
        getString(R.string.sort_newest_first),
        getString(R.string.sort_oldest_first),
        getString(R.string.sort_high_priority_first)
    )

    private fun localizedStatusOption(value: String): String {
        return when (value) {
            "Pending" -> getString(R.string.status_pending)
            "In Progress" -> getString(R.string.status_in_progress)
            "Resolved" -> getString(R.string.status_resolved)
            else -> getString(R.string.filter_all_statuses)
        }
    }

    private fun localizedPriorityOption(value: String): String {
        return when (value) {
            "High" -> getString(R.string.priority_high)
            "Medium" -> getString(R.string.priority_medium)
            "Low" -> getString(R.string.priority_low)
            else -> getString(R.string.filter_all_priorities)
        }
    }

    private fun localizedSortOption(value: String): String {
        return when (value) {
            "Oldest First" -> getString(R.string.sort_oldest_first)
            "High Priority First" -> getString(R.string.sort_high_priority_first)
            else -> getString(R.string.sort_newest_first)
        }
    }
}
