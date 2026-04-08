package com.example.urban.bottomNavigation.alert

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
