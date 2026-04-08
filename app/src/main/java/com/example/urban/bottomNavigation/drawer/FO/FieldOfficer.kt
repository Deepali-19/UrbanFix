package com.example.urban.bottomNavigation.drawer.FO

data class FieldOfficer(
var uid: String = "",
var name: String = "",
var department: String = "",
var phone: String = "",
var employeeId: String = "",
var city: String = "",
var profileImageUrl: String = "",
var inProgressCount: Int = 0,
var assignedCount: Int = 0,
var resolvedCount: Int = 0
)
