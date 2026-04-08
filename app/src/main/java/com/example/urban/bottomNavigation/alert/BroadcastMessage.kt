package com.example.urban.bottomNavigation.alert

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
