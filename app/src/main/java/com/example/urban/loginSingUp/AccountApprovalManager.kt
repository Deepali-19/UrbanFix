package com.example.urban.loginSingUp

import java.util.Locale

// This keeps all approval flow constants and helper rules in one place.
object AccountApprovalManager {

    const val STATUS_PENDING = "pending"
    const val STATUS_APPROVED = "approved"
    const val STATUS_REJECTED = "rejected"

    const val ROUTE_CITY_SUPER_ADMIN = "city_super_admin"
    const val ROUTE_ROOT_EMAIL = "root_email"

    const val ROLE_SUPER_ADMIN = "Super Admin"
    const val ROLE_DEPARTMENT_ADMIN = "Department Admin"
    const val ROLE_FIELD_OFFICER = "Field Officer"

    fun effectiveStatus(status: String?): String {
        return when (status?.trim()?.lowercase(Locale.ROOT)) {
            STATUS_PENDING -> STATUS_PENDING
            STATUS_REJECTED -> STATUS_REJECTED
            else -> STATUS_APPROVED
        }
    }

    fun normalizeCity(city: String): String {
        return city.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    fun roleCode(role: String): String {
        return when (role.trim()) {
            ROLE_SUPER_ADMIN -> "SA"
            ROLE_DEPARTMENT_ADMIN -> "DA"
            ROLE_FIELD_OFFICER -> "FO"
            else -> "OT"
        }
    }

    fun departmentCode(department: String): String {
        return when (department.trim().lowercase(Locale.ROOT)) {
            "water" -> "W"
            "roads" -> "R"
            "sanitation" -> "S"
            "electricity" -> "E"
            "general" -> "G"
            "all departments" -> "A"
            else -> department.uppercase(Locale.ROOT)
                .filter { it.isLetter() }
                .firstOrNull()
                ?.toString()
                ?: "X"
        }
    }

    fun employeeSeriesCode(role: String, department: String): String {
        val baseRoleCode = roleCode(role)
        return when (role.trim()) {
            ROLE_DEPARTMENT_ADMIN,
            ROLE_FIELD_OFFICER -> baseRoleCode + departmentCode(department)
            else -> baseRoleCode
        }
    }

    fun cityCode(city: String): String {
        val cleaned = city.uppercase(Locale.ROOT)
            .filter { it.isLetter() }
            .ifBlank { "CTY" }

        return when {
            cleaned.length >= 3 -> cleaned.take(3)
            else -> cleaned.padEnd(3, 'X')
        }
    }

    fun buildEmployeeId(city: String, role: String, department: String, runningNumber: Int): String {
        val safeNumber = runningNumber.coerceAtLeast(1)
        return "${cityCode(city)}-${employeeSeriesCode(role, department)}-${safeNumber.toString().padStart(3, '0')}"
    }

    fun pendingMessage(): String = "Account is pending"

    fun rejectedMessage(reason: String): String {
        return if (reason.isBlank()) {
            "Account request rejected"
        } else {
            "Account rejected: $reason"
        }
    }
}
