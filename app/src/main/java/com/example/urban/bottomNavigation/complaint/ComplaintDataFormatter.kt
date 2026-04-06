package com.example.urban.bottomNavigation.complaint

object ComplaintDataFormatter {

    fun resolvedDepartment(complaint: Complaint): String {
        val directDepartment = normalizeDepartment(complaint.departmentId)
        if (directDepartment != null) return directDepartment

        val issueType = complaint.issueType.trim().lowercase()
        return when {
            issueType.contains("water") || issueType.contains("pipe") || issueType.contains("leak") -> "Water"
            issueType.contains("road") || issueType.contains("street") || issueType.contains("pothole") -> "Roads"
            issueType.contains("garbage") || issueType.contains("waste") || issueType.contains("sanitation") || issueType.contains("sanitisation") || issueType.contains("drain") -> "Sanitation"
            issueType.contains("electric") || issueType.contains("light") || issueType.contains("power") -> "Electricity"
            else -> "General"
        }
    }

    fun normalizeDepartment(value: String): String? {
        return when (value.trim().lowercase()) {
            "" -> null
            "water" -> "Water"
            "road", "roads" -> "Roads"
            "sanitation", "sanitisation" -> "Sanitation"
            "electricity", "electric" -> "Electricity"
            else -> value.trim().takeIf { it.isNotBlank() }
        }
    }
}
