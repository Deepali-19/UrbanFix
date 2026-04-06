package com.example.urban.bottomNavigation.complaint

import com.google.firebase.database.Exclude

data class Complaint(
    val complaintId: String = "",
    val issueType: String = "",
    val status: Int = 0,  // 0 = Pending, 1 = In Progress, 2 = Resolved
    val title: String = "",
    val description: String = "",
    val civilianId: String = "",
    val phone: Long = 0L,
    var location: String = "",
    var latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val allottedOfficerId: String = "",
    val feedback: String = "",
    val images: ArrayList<String> = arrayListOf(),
    val timestamp: Long = 0L,
    val priority: Int = 0, // 0 = Low, 1 = Medium, 2 = High
    val validation: Boolean = false,
    val readByAdmin: Boolean = false,
    val departmentId: String = "",
) {
    @get:Exclude
    @set:Exclude
    var firebaseKey: String = ""
}
