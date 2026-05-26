package com.example.urban.loginSingUp

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

// This handles approval and rejection updates for the email-open approval screen.
object EmailApprovalProcessor {

    private const val ROOT_APPROVER = "root_email"
    private val database = FirebaseDatabase.getInstance().reference

    fun approveRequest(
        request: ApprovalRequest,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
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
                        onFailure(error?.message ?: "Unable to approve request")
                        return
                    }

                    val runningNumber = currentData?.getValue(Int::class.java) ?: 1
                    val employeeId = AccountApprovalManager.buildEmployeeId(
                        city = request.city,
                        role = request.role,
                        department = request.department,
                        runningNumber = runningNumber
                    )

                    saveApprovedState(request, employeeId, onSuccess, onFailure)
                }
            })
    }

    fun rejectRequest(
        request: ApprovalRequest,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val handledAt = System.currentTimeMillis()

        val updates = hashMapOf<String, Any>(
            "/Users/${request.uid}/accountStatus" to AccountApprovalManager.STATUS_REJECTED,
            "/Users/${request.uid}/rejectedAt" to handledAt,
            "/Users/${request.uid}/rejectedBy" to ROOT_APPROVER,
            "/Users/${request.uid}/rejectionReason" to reason,
            "/ApprovalRequests/${request.requestId}/status" to AccountApprovalManager.STATUS_REJECTED,
            "/ApprovalRequests/${request.requestId}/handledAt" to handledAt,
            "/ApprovalRequests/${request.requestId}/handledBy" to ROOT_APPROVER,
            "/ApprovalRequests/${request.requestId}/rejectionReason" to reason,
            "/ApprovalRequests/${request.requestId}/actionToken" to "",
            "/AccountNotifications/${request.uid}/${request.requestId}_rejected" to mapOf(
                "title" to "Account Rejected",
                "body" to "Your Urban Fix account request has been rejected.",
                "type" to "account_rejected",
                "timestamp" to handledAt,
                "reason" to reason
            )
        )

        database.updateChildren(updates)
            .addOnSuccessListener {
                ApprovalNotificationSender.sendDecisionNotification(
                    deviceToken = request.requesterDeviceToken,
                    title = "Account Rejected",
                    body = "Your Urban Fix account request has been rejected. Reason: $reason",
                    type = "account_rejected"
                )
                onSuccess()
            }
            .addOnFailureListener {
                onFailure(it.message ?: "Unable to reject request")
            }
    }

    private fun saveApprovedState(
        request: ApprovalRequest,
        employeeId: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val handledAt = System.currentTimeMillis()

        val updates = hashMapOf<String, Any>(
            "/Users/${request.uid}/employeeId" to employeeId,
            "/Users/${request.uid}/accountStatus" to AccountApprovalManager.STATUS_APPROVED,
            "/Users/${request.uid}/approvedAt" to handledAt,
            "/Users/${request.uid}/approvedBy" to ROOT_APPROVER,
            "/Users/${request.uid}/rejectedAt" to 0L,
            "/Users/${request.uid}/rejectedBy" to "",
            "/Users/${request.uid}/rejectionReason" to "",
            "/ApprovalRequests/${request.requestId}/status" to AccountApprovalManager.STATUS_APPROVED,
            "/ApprovalRequests/${request.requestId}/handledAt" to handledAt,
            "/ApprovalRequests/${request.requestId}/handledBy" to ROOT_APPROVER,
            "/ApprovalRequests/${request.requestId}/rejectionReason" to "",
            "/ApprovalRequests/${request.requestId}/actionToken" to "",
            "/AccountNotifications/${request.uid}/${request.requestId}_approved" to mapOf(
                "title" to "Account Approved",
                "body" to "Your Urban Fix account has been approved. You can now log in.",
                "type" to "account_approved",
                "timestamp" to handledAt
            )
        )

        database.updateChildren(updates)
            .addOnSuccessListener {
                ApprovalNotificationSender.sendDecisionNotification(
                    deviceToken = request.requesterDeviceToken,
                    title = "Account Approved",
                    body = "Your Urban Fix account has been approved. You can now log in.",
                    type = "account_approved"
                )
                onSuccess(employeeId)
            }
            .addOnFailureListener {
                onFailure(it.message ?: "Unable to approve request")
            }
    }
}
