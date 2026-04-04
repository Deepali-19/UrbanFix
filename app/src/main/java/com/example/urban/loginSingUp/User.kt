package com.example.urban.loginSingUp

data class  User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val department: String = "",
    val city: String = "",
    val deviceToken: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = 0L,
    val employeeId: String = "",
    val idProofUrl: String = "",
    val profileImageUrl: String = ""
)

