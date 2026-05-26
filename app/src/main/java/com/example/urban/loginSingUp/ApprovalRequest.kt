package com.example.urban.loginSingUp

// This model stores one pending approval request for admin account creation.
data class ApprovalRequest(
    val requestId: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val department: String = "",
    val city: String = "",
    val cityNormalized: String = "",
    val idProofUrl: String = "",
    val status: String = AccountApprovalManager.STATUS_PENDING,
    val approvalRoute: String = "",
    val targetApproverUid: String = "",
    val targetApproverEmail: String = "",
    val actionToken: String = "",
    val submittedAt: Long = 0L,
    val handledAt: Long = 0L,
    val handledBy: String = "",
    val rejectionReason: String = "",
    val requesterDeviceToken: String = ""
)
