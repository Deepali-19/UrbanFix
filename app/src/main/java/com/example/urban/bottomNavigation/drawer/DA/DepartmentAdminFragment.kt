package com.example.urban.bottomNavigation.drawer.DA

import android.os.Bundle
import android.view.View
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

class DepartmentAdminFragment : Fragment(R.layout.fragment_department_admin) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyView: View
    private lateinit var adapter: DepartmentAdminAdapter

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val items = ArrayList<DepartmentAdminProfile>()
    private var currentUserCityNormalized = ""
    private var isViewReady = false

    // This prepares the city-scoped department admin list for the current super admin.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewReady = true
        recyclerView = view.findViewById(R.id.recyclerDepartmentAdmins)
        loadingContainer = view.findViewById(R.id.departmentAdminLoadingContainer)
        emptyView = view.findViewById(R.id.tvDepartmentAdminEmpty)

        adapter = DepartmentAdminAdapter(items) { admin ->
            if (!canUseUi() || parentFragmentManager.isStateSaved) return@DepartmentAdminAdapter
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DepartmentAdminDetailFragment.newInstance(admin.uid))
                .addToBackStack(null)
                .commit()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadCurrentUserCityAndAdmins()
    }

    // This loads the logged-in super admin city before showing department admins from the same city.
    private fun loadCurrentUserCityAndAdmins() {
        showLoading(true)
        val currentUid = auth.currentUser?.uid ?: run {
            showLoading(false)
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
                loadDepartmentAdmins()
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                showLoading(false)
            }
    }

    // This loads approved department admins who belong to the same city as the current super admin.
    private fun loadDepartmentAdmins() {
        database.child("Users")
            .orderByChild("role")
            .equalTo(AccountApprovalManager.ROLE_DEPARTMENT_ADMIN)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!canUseUi()) return

                    val preparedList = snapshot.children.mapNotNull { child ->
                        val accountStatus = AccountApprovalManager.effectiveStatus(
                            child.child("accountStatus").value?.toString()
                        )
                        if (accountStatus != AccountApprovalManager.STATUS_APPROVED) return@mapNotNull null

                        val adminCityNormalized = ComplaintDataFormatter.normalizeCity(
                            child.child("cityNormalized").value?.toString().orEmpty()
                                .ifBlank { child.child("city").value?.toString().orEmpty() }
                        )
                        if (currentUserCityNormalized.isNotBlank() &&
                            adminCityNormalized != currentUserCityNormalized
                        ) return@mapNotNull null

                        DepartmentAdminProfile(
                            uid = child.key.orEmpty(),
                            name = child.child("name").value?.toString().orEmpty(),
                            department = child.child("department").value?.toString().orEmpty(),
                            employeeId = child.child("employeeId").value?.toString().orEmpty(),
                            city = child.child("city").value?.toString().orEmpty(),
                            profileImageUrl = child.child("profileImageUrl").value?.toString().orEmpty(),
                            email = child.child("email").value?.toString().orEmpty(),
                            idProofUrl = child.child("idProofUrl").value?.toString().orEmpty()
                        )
                    }.sortedBy { it.name.lowercase() }

                    adapter.updateItems(preparedList)
                    emptyView.visibility = if (preparedList.isEmpty()) View.VISIBLE else View.GONE
                    showLoading(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!canUseUi()) return
                    adapter.updateItems(emptyList())
                    emptyView.visibility = View.VISIBLE
                    showLoading(false)
                }
            })
    }

    // This toggles the loading and content states.
    private fun showLoading(isLoading: Boolean) {
        if (!canUseUi()) return
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        if (isLoading) {
            emptyView.visibility = View.GONE
        }
    }

    // This clears the safe-view flag once the fragment view goes away.
    override fun onDestroyView() {
        isViewReady = false
        super.onDestroyView()
    }

    // This returns true only when the fragment can still update UI safely.
    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }
}
