package com.example.urban.bottomNavigation.drawer.DA

// This model stores the department admin profile details used in the super admin drawer screen.
data class DepartmentAdminProfile(
    var uid: String = "",
    var name: String = "",
    var department: String = "",
    var employeeId: String = "",
    var city: String = "",
    var profileImageUrl: String = "",
    var email: String = "",
    var idProofUrl: String = ""
)
