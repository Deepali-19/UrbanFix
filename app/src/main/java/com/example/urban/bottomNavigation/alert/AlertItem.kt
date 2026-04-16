package com.example.urban.bottomNavigation.alert

// This model represents one saved notification entry shown in the alert center.
data class AlertItem(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val complaintKey: String = "",
    val complaintDisplayId: String = ""
)
