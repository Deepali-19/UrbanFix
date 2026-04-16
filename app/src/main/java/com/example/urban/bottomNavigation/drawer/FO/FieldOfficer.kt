package com.example.urban.bottomNavigation.drawer.FO

// This model stores the officer profile details and complaint workload used in the drawer screens.
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
