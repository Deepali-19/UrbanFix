package com.example.urban.bottomNavigation.alert

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.ComplaintDetailFragment
import com.example.urban.loginSingUp.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class AlertsFragment : Fragment(R.layout.fragment_alerts) {

    private lateinit var loadingContainer: View
    private lateinit var contentContainer: View
    private lateinit var emptyState: View
    private lateinit var rvAlerts: RecyclerView
    private lateinit var tvAlertSubtitle: TextView
    private lateinit var tvBroadcastAccess: TextView
    private lateinit var tvAlertTotal: TextView
    private lateinit var tvAlertUnread: TextView
    private lateinit var tvAlertRead: TextView
    private lateinit var btnMarkAllRead: MaterialButton
    private lateinit var btnClearAlerts: MaterialButton
    private lateinit var fabBroadcast: FloatingActionButton

    private lateinit var adapter: AlertAdapter
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var currentUserName = ""
    private var currentUserRole = ""
    private var currentUserDepartment = ""

    private val supportedDepartments = listOf(
        "Water",
        "Roads",
        "Sanitation",
        "Electricity"
    )
    private val broadcastDepartmentOptions = listOf("All Departments") + supportedDepartments

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadingContainer = view.findViewById(R.id.alertsLoadingContainer)
        contentContainer = view.findViewById(R.id.alertsContentContainer)
        emptyState = view.findViewById(R.id.emptyAlertsState)
        rvAlerts = view.findViewById(R.id.rvAlerts)
        tvAlertSubtitle = view.findViewById(R.id.tvAlertSubtitle)
        tvBroadcastAccess = view.findViewById(R.id.tvBroadcastAccess)
        tvAlertTotal = view.findViewById(R.id.tvAlertTotal)
        tvAlertUnread = view.findViewById(R.id.tvAlertUnread)
        tvAlertRead = view.findViewById(R.id.tvAlertRead)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        btnClearAlerts = view.findViewById(R.id.btnClearAlerts)
        fabBroadcast = view.findViewById(R.id.fabBroadcast)

        adapter = AlertAdapter { alert ->
            AlertStorage.markRead(requireContext(), alert.id)
            refreshAlerts()

            val complaintKey = alert.complaintKey.ifBlank { alert.complaintDisplayId }
            if (complaintKey.isNotBlank()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ComplaintDetailFragment.newInstance(complaintKey))
                    .addToBackStack(null)
                    .commit()
            }
        }

        rvAlerts.layoutManager = LinearLayoutManager(requireContext())
        rvAlerts.adapter = adapter

        btnMarkAllRead.setOnClickListener {
            AlertStorage.markAllRead(requireContext())
            refreshAlerts()
        }

        btnClearAlerts.setOnClickListener {
            AlertStorage.clearAll(requireContext())
            refreshAlerts()
        }

        fabBroadcast.setOnClickListener {
            showBroadcastDialog()
        }

        loadCurrentUserAccess()
        refreshAlerts()
    }

    override fun onResume() {
        super.onResume()
        refreshAlerts()
    }

    private fun refreshAlerts() {
        val alerts = AlertStorage.getAlerts(requireContext())
        val unreadCount = alerts.count { !it.isRead }

        adapter.updateItems(alerts)
        tvAlertTotal.text = "Total: ${alerts.size}"
        tvAlertUnread.text = "Unread: $unreadCount"
        tvAlertRead.text = "Read: ${alerts.size - unreadCount}"

        emptyState.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
        contentContainer.visibility = View.VISIBLE
        loadingContainer.visibility = View.GONE
    }

    private fun loadCurrentUserAccess() {
        val uid = auth.currentUser?.uid ?: run {
            configureBroadcastAccess()
            return
        }

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                currentUserName = user?.name.orEmpty()
                currentUserRole = user?.role.orEmpty()
                currentUserDepartment = normalizeDepartment(user?.department.orEmpty())
                configureBroadcastAccess()
            }
            .addOnFailureListener {
                configureBroadcastAccess()
            }
    }

    // Broadcast is intentionally restricted: Super Admin can target anyone, Department Admin only their own department.
    private fun configureBroadcastAccess() {
        when (currentUserRole) {
            "Super Admin" -> {
                fabBroadcast.visibility = View.VISIBLE
                tvBroadcastAccess.visibility = View.VISIBLE
                tvBroadcastAccess.text = "You can broadcast city-wide notices or department-specific updates."
                tvAlertSubtitle.text = "FCM alerts, complaint updates, operational notifications, and civilian broadcasts."
            }

            "Department Admin" -> {
                if (isSupportedDepartment(currentUserDepartment)) {
                    fabBroadcast.visibility = View.VISIBLE
                    tvBroadcastAccess.visibility = View.VISIBLE
                    tvBroadcastAccess.text = "You can broadcast only to $currentUserDepartment civilians."
                    tvAlertSubtitle.text = "Department alerts, complaint updates, and broadcast communication live here."
                } else {
                    fabBroadcast.visibility = View.GONE
                    tvBroadcastAccess.visibility = View.VISIBLE
                    tvBroadcastAccess.text = "Broadcast is locked until this admin account is mapped to Water, Roads, Sanitation, or Electricity."
                    tvAlertSubtitle.text = "Complaint updates and operational notifications live here."
                }
            }

            else -> {
                fabBroadcast.visibility = View.GONE
                tvBroadcastAccess.visibility = View.GONE
                tvAlertSubtitle.text = "FCM alerts, complaint updates, and operational notifications."
            }
        }
    }

    private fun showBroadcastDialog() {
        if (currentUserRole != "Super Admin" && currentUserRole != "Department Admin") {
            Toast.makeText(requireContext(), "Broadcast is available only for admin users.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_broadcast_message, null, false)

        val tilDepartment = dialogView.findViewById<TextInputLayout>(R.id.tilBroadcastDepartment)
        val actDepartment = dialogView.findViewById<AutoCompleteTextView>(R.id.actBroadcastDepartment)
        val tvLockedDepartment = dialogView.findViewById<TextView>(R.id.tvLockedDepartment)
        val tvBroadcastScope = dialogView.findViewById<TextView>(R.id.tvBroadcastScope)
        val etTitle = dialogView.findViewById<EditText>(R.id.etBroadcastTitle)
        val etBody = dialogView.findViewById<EditText>(R.id.etBroadcastBody)
        val tilTitle = dialogView.findViewById<TextInputLayout>(R.id.tilBroadcastTitle)
        val tilBody = dialogView.findViewById<TextInputLayout>(R.id.tilBroadcastBody)

        if (currentUserRole == "Super Admin") {
            tilDepartment.visibility = View.VISIBLE
            tvLockedDepartment.visibility = View.GONE
            tvBroadcastScope.text = "Choose a department or keep it city-wide for all civilians."
            actDepartment.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    broadcastDepartmentOptions
                )
            )
            actDepartment.setText(broadcastDepartmentOptions.first(), false)
            actDepartment.setOnClickListener { actDepartment.showDropDown() }
            actDepartment.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) actDepartment.showDropDown()
            }
        } else {
            tilDepartment.visibility = View.GONE
            tvLockedDepartment.visibility = View.VISIBLE
            tvLockedDepartment.text = "Target: ${currentUserDepartment.ifBlank { "Your Department" }} civilians"
            tvBroadcastScope.text = "Department Admin broadcasts are locked to your own department."
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().trim()
                val body = etBody.text.toString().trim()
                val targetDepartment = if (currentUserRole == "Super Admin") {
                    normalizeDepartment(actDepartment.text.toString())
                        .ifBlank { "All Departments" }
                } else {
                    currentUserDepartment
                }

                tilTitle.error = null
                tilBody.error = null
                tilDepartment.error = null

                if (title.isBlank()) {
                    tilTitle.error = "Enter a title"
                    return@setOnClickListener
                }

                if (body.isBlank()) {
                    tilBody.error = "Enter a message"
                    return@setOnClickListener
                }

                if (currentUserRole == "Super Admin" && targetDepartment.isBlank()) {
                    tilDepartment.error = "Choose a target department"
                    return@setOnClickListener
                }

                if (targetDepartment != "All Departments" && !isSupportedDepartment(targetDepartment)) {
                    if (currentUserRole == "Super Admin") {
                        tilDepartment.error = "Choose Water, Roads, Sanitation, Electricity, or All Departments"
                    } else {
                        Toast.makeText(requireContext(), "Your admin account needs a valid department to send broadcasts.", Toast.LENGTH_SHORT).show()
                    }
                    return@setOnClickListener
                }

                sendBroadcastMessage(
                    title = title,
                    body = body,
                    targetDepartment = targetDepartment,
                    dialog = dialog
                )
            }
        }

        dialog.show()
    }

    // Broadcasts are written to Firebase so the civilian app can listen and display the same message live.
    private fun sendBroadcastMessage(
        title: String,
        body: String,
        targetDepartment: String,
        dialog: AlertDialog
    ) {
        val uid = auth.currentUser?.uid ?: return
        val pushKey = database.child("BroadcastMessages").push().key ?: UUID.randomUUID().toString()
        val message = BroadcastMessage(
            id = pushKey,
            title = title,
            body = body,
            targetDepartment = targetDepartment,
            senderId = uid,
            senderName = currentUserName,
            senderRole = currentUserRole,
            senderDepartment = currentUserDepartment,
            createdAt = System.currentTimeMillis()
        )

        database.child("BroadcastMessages")
            .child(pushKey)
            .setValue(message)
            .addOnSuccessListener {
                val audienceLabel = if (targetDepartment == "All Departments") {
                    "all civilians"
                } else {
                    "$targetDepartment civilians"
                }

                AlertStorage.addAlert(
                    requireContext(),
                    AlertItem(
                        id = "broadcast_$pushKey",
                        title = "Broadcast sent",
                        body = "$title was sent to $audienceLabel.",
                        type = "Broadcast",
                        timestamp = System.currentTimeMillis()
                    )
                )
                refreshAlerts()
                Toast.makeText(requireContext(), "Broadcast sent to $audienceLabel.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), error.message ?: "Broadcast failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun normalizeDepartment(value: String): String {
        return when (value.trim().lowercase()) {
            "all departments", "all" -> "All Departments"
            "water" -> "Water"
            "road", "roads" -> "Roads"
            "sanitation", "sanitisation" -> "Sanitation"
            "electricity", "electric" -> "Electricity"
            else -> value.trim()
        }
    }

    private fun isSupportedDepartment(value: String): Boolean =
        supportedDepartments.contains(normalizeDepartment(value))
}
