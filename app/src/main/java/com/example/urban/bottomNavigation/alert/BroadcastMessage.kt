package com.example.urban.bottomNavigation.alert

// This model stores one broadcast message that can be sent to civilians from the admin app.
data class BroadcastMessage(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val targetDepartment: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val senderDepartment: String = "",
    val createdAt: Long = 0L
)
