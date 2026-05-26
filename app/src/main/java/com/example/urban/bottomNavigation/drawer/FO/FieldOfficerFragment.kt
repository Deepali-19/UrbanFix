package com.example.urban.bottomNavigation.drawer.FO

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.loginSingUp.AccountApprovalManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FieldOfficerFragment : Fragment(R.layout.fragment_field_officer) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyView: View
    private lateinit var tvOfficerTotal: TextView
    private lateinit var tvOfficerActive: TextView
    private lateinit var tvOfficerBusy: TextView
    private val list = ArrayList<FieldOfficer>()
    private lateinit var adapter: FieldOfficerAdapter
    private var isViewReady = false
    private val auth = FirebaseAuth.getInstance()
    private var currentUserCityNormalized = ""

    private val database = FirebaseDatabase.getInstance().reference

    // Sets up the officer list screen.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isViewReady = true
        recyclerView = view.findViewById(R.id.recyclerView)
        loadingContainer = view.findViewById(R.id.officerLoadingContainer)
        emptyView = view.findViewById(R.id.tvOfficerEmpty)
        tvOfficerTotal = view.findViewById(R.id.tvOfficerTotal)
        tvOfficerActive = view.findViewById(R.id.tvOfficerActive)
        tvOfficerBusy = view.findViewById(R.id.tvOfficerBusy)

        adapter = FieldOfficerAdapter(list) { officer ->
            if (!canUseUi() || parentFragmentManager.isStateSaved) return@FieldOfficerAdapter
            val fragment = OfficerDetailFragment.newInstance(officer.uid)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadCurrentUserCityAndOfficers()
    }

    // Loads the current user's city first so the officer list stays inside the same city scope.
    private fun loadCurrentUserCityAndOfficers() {
        val currentUid = auth.currentUser?.uid ?: run {
            loadOfficers()
            return
        }

        database.child("Users").child(currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!canUseUi()) return@addOnSuccessListener
                currentUserCityNormalized = ComplaintDataFormatter.normalizeCity(
                    snapshot.child("cityNormalized").value?.toString().orEmpty()
                        .ifBlank { snapshot.child("city").value?.toString().orEmpty() }
                )
                loadOfficers()
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                loadOfficers()
            }
    }

    // Loads all field officers visible to the current admin city.
    private fun loadOfficers() {
        showLoading(true)

        database.child("Users")
            .orderByChild("role")
            .equalTo("Field Officer")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!canUseUi()) return
                    val officers = ArrayList<FieldOfficer>()

                    for (data in snapshot.children) {
                        val officer = data.getValue(FieldOfficer::class.java) ?: continue
                        val accountStatus = AccountApprovalManager.effectiveStatus(
                            data.child("accountStatus").value?.toString()
                        )
                        if (accountStatus != AccountApprovalManager.STATUS_APPROVED) continue
                        val officerCityNormalized = ComplaintDataFormatter.normalizeCity(
                            data.child("cityNormalized").value?.toString().orEmpty()
                                .ifBlank { data.child("city").value?.toString().orEmpty() }
                        )
                        if (currentUserCityNormalized.isNotBlank() &&
                            officerCityNormalized != currentUserCityNormalized
                        ) continue
                        officer.uid = data.key.orEmpty()
                        officers.add(officer)
                    }

                    if (officers.isEmpty()) {
                        list.clear()
                        adapter.notifyDataSetChanged()
                        updateSummary()
                        showLoading(false)
                        return
                    }

                    loadComplaintMetrics(officers)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!canUseUi()) return
                    list.clear()
                    adapter.notifyDataSetChanged()
                    updateSummary()
                    showLoading(false)
                }
            })
    }

    // Loads complaint counts for each officer.
    private fun loadComplaintMetrics(officers: List<FieldOfficer>) {
        val officerMap = officers.associateBy { it.uid }

        database.child("Complaints")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!canUseUi()) return
                    val preparedList = officers.map { officer ->
                        officer.copy(
                            assignedCount = 0,
                            inProgressCount = 0,
                            resolvedCount = 0
                        )
                    }.associateBy { it.uid }.toMutableMap()

                    for (item in snapshot.children) {
                        val allottedOfficerId = item.child("allottedOfficerId").value?.toString().orEmpty()
                        val targetOfficer = preparedList[allottedOfficerId] ?: continue
                        val status = item.child("status").getValue(Int::class.java) ?: 0

                        targetOfficer.assignedCount += 1
                        if (status == 1) {
                            targetOfficer.inProgressCount += 1
                        }
                        if (status == 2) {
                            targetOfficer.resolvedCount += 1
                        }
                    }

                    list.clear()
                    list.addAll(preparedList.values.sortedBy { it.name.lowercase() })
                    adapter.notifyDataSetChanged()
                    updateSummary()
                    showLoading(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!canUseUi()) return
                    list.clear()
                    list.addAll(officers.filter { officerMap.containsKey(it.uid) })
                    adapter.notifyDataSetChanged()
                    updateSummary()
                    showLoading(false)
                }
            })
    }

    // Updates summary and empty state.
    private fun updateSummary() {
        val totalOfficers = list.size
        val activeOfficers = list.count { it.assignedCount > 0 || it.inProgressCount > 0 }
        val busyOfficers = list.count { it.inProgressCount >= 3 || it.assignedCount >= 5 }

        tvOfficerTotal.text = "Officers $totalOfficers"
        tvOfficerActive.text = "Active $activeOfficers"
        tvOfficerBusy.text = "Busy $busyOfficers"
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    // Toggles loading state.
    private fun showLoading(isLoading: Boolean) {
        if (!canUseUi()) return
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        if (isLoading) {
            emptyView.visibility = View.GONE
        }
    }

    // Clears the ready flag when the view goes away.
    override fun onDestroyView() {
        isViewReady = false
        super.onDestroyView()
    }

    // Returns true only when the fragment can still update UI safely.
    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }
}
