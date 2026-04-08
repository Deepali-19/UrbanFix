package com.example.urban.bottomNavigation.complaint

object ComplaintDataFormatter {

    fun hasCoordinates(complaint: Complaint): Boolean {
        return complaint.latitude != 0.0 || complaint.longitude != 0.0
    }

    fun locationLabel(complaint: Complaint): String {
        val location = complaint.location.trim()
        return when {
            location.isNotBlank() -> location
            hasCoordinates(complaint) -> "Coordinates available"
            else -> "Location not available"
        }
    }

    fun coordinatesLabel(complaint: Complaint): String {
        return if (hasCoordinates(complaint)) {
            String.format("%.5f, %.5f", complaint.latitude, complaint.longitude)
        } else {
            "Coordinates not available"
        }
    }

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
