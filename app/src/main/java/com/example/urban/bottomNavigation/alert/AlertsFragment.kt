package com.example.urban.bottomNavigation.alert

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.PopupWindow
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
    private lateinit var fabBroadcast: FloatingActionButton
    private var broadcastTooltip: PopupWindow? = null

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
    private var isViewReady = false

    // Sets up the alerts screen.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isViewReady = true
        loadingContainer = view.findViewById(R.id.alertsLoadingContainer)
        contentContainer = view.findViewById(R.id.alertsContentContainer)
        emptyState = view.findViewById(R.id.emptyAlertsState)
        rvAlerts = view.findViewById(R.id.rvAlerts)
        fabBroadcast = view.findViewById(R.id.fabBroadcast)

        adapter = AlertAdapter { alert ->
            if (!canUseUi()) return@AlertAdapter
            AlertStorage.markRead(requireContext(), alert.id)
            refreshAlerts()

            val complaintKey = alert.complaintKey.ifBlank { alert.complaintDisplayId }
            if (complaintKey.isNotBlank() && !parentFragmentManager.isStateSaved) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ComplaintDetailFragment.newInstance(complaintKey))
                    .addToBackStack(null)
                    .commit()
            }
        }

        rvAlerts.layoutManager = LinearLayoutManager(requireContext())
        rvAlerts.adapter = adapter

        fabBroadcast.setOnClickListener {
            dismissBroadcastTooltip()
            showBroadcastDialog()
        }

        fabBroadcast.setOnLongClickListener {
            showBroadcastTooltip(it)
            true
        }

        loadCurrentUserAccess()
        refreshAlerts()
    }

    // Reload alerts when coming back.
    override fun onResume() {
        super.onResume()
        if (!canUseUi()) return
        refreshAlerts()
    }

    // Clears the small popup label when the view closes.
    override fun onDestroyView() {
        isViewReady = false
        dismissBroadcastTooltip()
        super.onDestroyView()
    }

    // Loads alerts and updates counters.
    private fun refreshAlerts() {
        if (!canUseUi()) return
        val alerts = AlertStorage.getAlerts(requireContext())

        adapter.updateItems(alerts)
        emptyState.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
        contentContainer.visibility = View.VISIBLE
        loadingContainer.visibility = View.GONE
    }

    // Loads user role for broadcast access.
    private fun loadCurrentUserAccess() {
        val uid = auth.currentUser?.uid ?: run {
            configureBroadcastAccess()
            return
        }

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!canUseUi()) return@addOnSuccessListener
                val user = snapshot.getValue(User::class.java)
                currentUserName = user?.name.orEmpty()
                currentUserRole = user?.role.orEmpty()
                currentUserDepartment = normalizeDepartment(user?.department.orEmpty())
                configureBroadcastAccess()
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                configureBroadcastAccess()
            }
    }

    // Shows or hides broadcast controls.
    private fun configureBroadcastAccess() {
        if (!canUseUi()) return
        when (currentUserRole) {
            "Super Admin" -> {
                fabBroadcast.visibility = View.VISIBLE
            }

            "Department Admin" -> {
                if (isSupportedDepartment(currentUserDepartment)) {
                    fabBroadcast.visibility = View.VISIBLE
                } else {
                    fabBroadcast.visibility = View.GONE
                }
            }

            else -> {
                fabBroadcast.visibility = View.GONE
            }
        }
    }

    // Marks every alert as read from the toolbar action.
    fun markAllReadFromToolbar() {
        if (!canUseUi()) return
        AlertStorage.markAllRead(requireContext())
        refreshAlerts()
    }

    // Shows a rounded grey helper label above the broadcast button.
    private fun showBroadcastTooltip(anchor: View) {
        if (!canUseUi()) return

        dismissBroadcastTooltip()

        val tooltipView = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_broadcast_tooltip, null, false)

        val popupWindow = PopupWindow(
            tooltipView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            elevation = 10f
        }

        tooltipView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )

        val xOffset = (anchor.width - tooltipView.measuredWidth) / 2
        val yOffset = -(tooltipView.measuredHeight + anchor.height + 18)

        popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.START)
        broadcastTooltip = popupWindow

        anchor.postDelayed({
            dismissBroadcastTooltip()
        }, 1600)
    }

    // Hides the helper label if it is already visible.
    private fun dismissBroadcastTooltip() {
        broadcastTooltip?.dismiss()
        broadcastTooltip = null
    }

    // Opens the broadcast dialog.
    private fun showBroadcastDialog() {
        if (!canUseUi()) return
        if (currentUserRole != "Super Admin" && currentUserRole != "Department Admin") {
            Toast.makeText(requireContext(), getString(R.string.alerts_broadcast_admin_only), Toast.LENGTH_SHORT).show()
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
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnBroadcastCancel)
        val btnSend = dialogView.findViewById<MaterialButton>(R.id.btnBroadcastSend)

        if (currentUserRole == "Super Admin") {
            tilDepartment.visibility = View.VISIBLE
            tvLockedDepartment.visibility = View.GONE
            tvBroadcastScope.text = getString(R.string.alerts_broadcast_scope_super)
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
            tvLockedDepartment.text = getString(
                R.string.alerts_broadcast_target_department,
                currentUserDepartment.ifBlank { getString(R.string.alerts_your_department) }
            )
            tvBroadcastScope.text = getString(R.string.alerts_broadcast_scope_department)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSend.setOnClickListener {
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
                tilTitle.error = getString(R.string.alerts_enter_title)
                return@setOnClickListener
            }

            if (body.isBlank()) {
                tilBody.error = getString(R.string.alerts_enter_message)
                return@setOnClickListener
            }

            if (currentUserRole == "Super Admin" && targetDepartment.isBlank()) {
                tilDepartment.error = getString(R.string.alerts_choose_target_department)
                return@setOnClickListener
            }

            if (targetDepartment != "All Departments" && !isSupportedDepartment(targetDepartment)) {
                if (currentUserRole == "Super Admin") {
                    tilDepartment.error = getString(R.string.alerts_choose_supported_department)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.alerts_invalid_department_account), Toast.LENGTH_SHORT).show()
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

        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    // Sends the broadcast message.
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
                if (!canUseUi()) return@addOnSuccessListener
                val audienceLabel = if (targetDepartment == "All Departments") {
                    "all civilians"
                } else {
                    "$targetDepartment civilians"
                }

                AlertStorage.addAlert(
                    requireContext(),
                    AlertItem(
                        id = "broadcast_$pushKey",
                        title = getString(R.string.alerts_broadcast_sent_title),
                        body = getString(R.string.alerts_broadcast_sent_body, title, audienceLabel),
                        type = getString(R.string.alert_type_broadcast),
                        timestamp = System.currentTimeMillis()
                    )
                )
                refreshAlerts()
                Toast.makeText(requireContext(), getString(R.string.alerts_broadcast_sent_to, audienceLabel), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener { error ->
                if (!canUseUi()) return@addOnFailureListener
                Toast.makeText(requireContext(), error.message ?: getString(R.string.alerts_broadcast_failed), Toast.LENGTH_SHORT).show()
            }
    }

    // This tells the fragment whether it is still safe to touch UI or context.
    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }

    // Normalizes department names.
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

    // Checks supported departments.
    private fun isSupportedDepartment(value: String): Boolean =
        supportedDepartments.contains(normalizeDepartment(value))
}
