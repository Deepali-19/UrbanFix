package com.example.urban.bottomNavigation.approvals

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.loginSingUp.AccountApprovalManager
import com.example.urban.loginSingUp.ApprovalNotificationSender
import com.example.urban.loginSingUp.ApprovalRequest
import com.example.urban.loginSingUp.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

// This screen lets a city super admin approve or reject new staff account requests.
class ApprovalsFragment : Fragment(R.layout.fragment_approvals) {

    private lateinit var loadingContainer: View
    private lateinit var contentContainer: View
    private lateinit var emptyState: View
    private lateinit var subtitleView: TextView
    private lateinit var adapter: ApprovalRequestAdapter

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private var approvalsListener: ValueEventListener? = null
    private var currentUser: User? = null
    private var isViewReady = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isViewReady = true
        loadingContainer = view.findViewById(R.id.approvalsLoadingContainer)
        contentContainer = view.findViewById(R.id.approvalsContentContainer)
        emptyState = view.findViewById(R.id.approvalsEmptyState)
        subtitleView = view.findViewById(R.id.tvApprovalsSubtitle)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvApprovals)
        adapter = ApprovalRequestAdapter(
            onViewId = { openIdPreview(it.idProofUrl) },
            onAccept = { approveRequest(it) },
            onReject = { openRejectDialog(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadCurrentUser()
    }

    // This loads the logged-in user before listening to city approval requests.
    private fun loadCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!canUseUi()) return@addOnSuccessListener
                currentUser = snapshot.getValue(User::class.java)?.copy(uid = uid)
                listenToApprovals()
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                showLoading(false)
            }
    }

    // This keeps the approval list live for the current super admin.
    private fun listenToApprovals() {
        val uid = currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            showLoading(false)
            return
        }

        approvalsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!canUseUi()) return

                val items = snapshot.children.mapNotNull { child ->
                    child.getValue(ApprovalRequest::class.java)
                }.filter {
                    it.status == AccountApprovalManager.STATUS_PENDING &&
                        it.approvalRoute == AccountApprovalManager.ROUTE_CITY_SUPER_ADMIN &&
                        it.targetApproverUid == uid
                }.sortedByDescending { it.submittedAt }

                subtitleView.text = if (items.isEmpty()) {
                    getString(R.string.approval_empty_body)
                } else {
                    getString(R.string.approval_request_details)
                }

                adapter.updateItems(items)
                emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                contentContainer.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                showLoading(false)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!canUseUi()) return
                showLoading(false)
                Toast.makeText(requireContext(), getString(R.string.approval_action_failed), Toast.LENGTH_SHORT).show()
            }
        }

        database.child("ApprovalRequests").addValueEventListener(approvalsListener as ValueEventListener)
    }

    // This starts the approval flow and generates the employee ID counter safely.
    private fun approveRequest(request: ApprovalRequest) {
        val cityCode = AccountApprovalManager.cityCode(request.city)
        val seriesCode = AccountApprovalManager.employeeSeriesCode(request.role, request.department)

        database.child("EmployeeIdCounters")
            .child(cityCode)
            .child(seriesCode)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentValue = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentValue + 1
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (!committed || error != null) {
                        if (canUseUi()) {
                            Toast.makeText(requireContext(), getString(R.string.approval_action_failed), Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val runningNumber = currentData?.getValue(Int::class.java) ?: 1
                    val employeeId = AccountApprovalManager.buildEmployeeId(
                        city = request.city,
                        role = request.role,
                        department = request.department,
                        runningNumber = runningNumber
                    )
                    completeApproval(request, employeeId)
                }
            })
    }

    // This saves the approved account status and sends the approval notification.
    private fun completeApproval(request: ApprovalRequest, employeeId: String) {
        val handledAt = System.currentTimeMillis()
        val approverUid = currentUser?.uid.orEmpty()

        val updates = hashMapOf<String, Any>(
            "/Users/${request.uid}/employeeId" to employeeId,
            "/Users/${request.uid}/accountStatus" to AccountApprovalManager.STATUS_APPROVED,
            "/Users/${request.uid}/approvedAt" to handledAt,
            "/Users/${request.uid}/approvedBy" to approverUid,
            "/Users/${request.uid}/rejectedAt" to 0L,
            "/Users/${request.uid}/rejectedBy" to "",
            "/Users/${request.uid}/rejectionReason" to "",
            "/ApprovalRequests/${request.requestId}/status" to AccountApprovalManager.STATUS_APPROVED,
            "/ApprovalRequests/${request.requestId}/handledAt" to handledAt,
            "/ApprovalRequests/${request.requestId}/handledBy" to approverUid,
            "/ApprovalRequests/${request.requestId}/rejectionReason" to "",
            "/AccountNotifications/${request.uid}/${request.requestId}_approved" to mapOf(
                "title" to getString(R.string.approval_notification_approved_title),
                "body" to getString(R.string.approval_notification_approved_body),
                "type" to "account_approved",
                "timestamp" to handledAt
            )
        )

        database.updateChildren(updates)
            .addOnSuccessListener {
                ApprovalNotificationSender.sendDecisionNotification(
                    deviceToken = request.requesterDeviceToken,
                    title = getString(R.string.approval_notification_approved_title),
                    body = getString(R.string.approval_notification_approved_body),
                    type = "account_approved"
                )
                if (canUseUi()) {
                    Toast.makeText(requireContext(), getString(R.string.approval_accept_success), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (canUseUi()) {
                    Toast.makeText(requireContext(), getString(R.string.approval_action_failed), Toast.LENGTH_SHORT).show()
                }
            }
    }

    // This opens a small reason dialog before the request is rejected.
    private fun openRejectDialog(request: ApprovalRequest) {
        if (!canUseUi()) return

        val input = EditText(requireContext())
        input.hint = getString(R.string.approval_reject_hint)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.approval_reject_title)
            .setView(input)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.approval_reject, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reason = input.text.toString().trim()
                        if (reason.isBlank()) {
                            input.error = getString(R.string.approval_reject_required)
                            return@setOnClickListener
                        }

                        dismiss()
                        rejectRequest(request, reason)
                    }
                }
            }
            .show()
    }

    // This saves the rejected status and notifies the requester.
    private fun rejectRequest(request: ApprovalRequest, reason: String) {
        val handledAt = System.currentTimeMillis()
        val approverUid = currentUser?.uid.orEmpty()

        val updates = hashMapOf<String, Any>(
            "/Users/${request.uid}/accountStatus" to AccountApprovalManager.STATUS_REJECTED,
            "/Users/${request.uid}/rejectedAt" to handledAt,
            "/Users/${request.uid}/rejectedBy" to approverUid,
            "/Users/${request.uid}/rejectionReason" to reason,
            "/ApprovalRequests/${request.requestId}/status" to AccountApprovalManager.STATUS_REJECTED,
            "/ApprovalRequests/${request.requestId}/handledAt" to handledAt,
            "/ApprovalRequests/${request.requestId}/handledBy" to approverUid,
            "/ApprovalRequests/${request.requestId}/rejectionReason" to reason,
            "/AccountNotifications/${request.uid}/${request.requestId}_rejected" to mapOf(
                "title" to getString(R.string.approval_notification_rejected_title),
                "body" to getString(R.string.approval_notification_rejected_body),
                "type" to "account_rejected",
                "timestamp" to handledAt,
                "reason" to reason
            )
        )

        database.updateChildren(updates)
            .addOnSuccessListener {
                ApprovalNotificationSender.sendDecisionNotification(
                    deviceToken = request.requesterDeviceToken,
                    title = getString(R.string.approval_notification_rejected_title),
                    body = "${getString(R.string.approval_notification_rejected_body)} Reason: $reason",
                    type = "account_rejected"
                )
                if (canUseUi()) {
                    Toast.makeText(requireContext(), getString(R.string.approval_reject_success), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (canUseUi()) {
                    Toast.makeText(requireContext(), getString(R.string.approval_action_failed), Toast.LENGTH_SHORT).show()
                }
            }
    }

    // This shows the uploaded ID proof in the same full-screen preview style used elsewhere.
    private fun openIdPreview(imageUrl: String) {
        if (!canUseUi() || imageUrl.isBlank()) return

        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_complaint_image_preview)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val imageView = dialog.findViewById<ImageView>(R.id.imgComplaintPreview)
        val closeButton = dialog.findViewById<ImageView>(R.id.btnClosePreview)

        Glide.with(requireContext())
            .load(imageUrl)
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .into(imageView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // This keeps the loading and content containers in sync.
    private fun showLoading(isLoading: Boolean) {
        if (!canUseUi()) return
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        isViewReady = false
        approvalsListener?.let {
            database.child("ApprovalRequests").removeEventListener(it)
        }
        approvalsListener = null
        super.onDestroyView()
    }

    // This makes sure the fragment still has a valid screen before it updates UI.
    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }
}
