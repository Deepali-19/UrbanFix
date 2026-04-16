package com.example.urban.bottomNavigation.complaint

import android.content.Context
import com.example.urban.R

object ComplaintDataFormatter {

    // This checks whether a complaint has valid coordinate data.
    fun hasCoordinates(complaint: Complaint): Boolean {
        return complaint.latitude != 0.0 || complaint.longitude != 0.0
    }

    // This returns the best readable location label for complaint UI.
    fun locationLabel(complaint: Complaint): String {
        val location = complaint.location.trim()
        return when {
            location.isNotBlank() -> location
            hasCoordinates(complaint) -> "Coordinates available"
            else -> "Location not available"
        }
    }

    // This returns the latitude and longitude text shown in complaint detail screens.
    fun coordinatesLabel(complaint: Complaint): String {
        return if (hasCoordinates(complaint)) {
            String.format("%.5f, %.5f", complaint.latitude, complaint.longitude)
        } else {
            "Coordinates not available"
        }
    }

    // This returns the translated location label for UI screens that use string resources.
    fun localizedLocationLabel(context: Context, complaint: Complaint): String {
        val location = complaint.location.trim()
        return when {
            location.isNotBlank() -> location
            hasCoordinates(complaint) -> context.getString(R.string.complaint_coordinates_available)
            else -> context.getString(R.string.complaint_location_not_available)
        }
    }

    // This returns the translated coordinates label for UI screens that use string resources.
    fun localizedCoordinatesLabel(context: Context, complaint: Complaint): String {
        return if (hasCoordinates(complaint)) {
            String.format("%.5f, %.5f", complaint.latitude, complaint.longitude)
        } else {
            context.getString(R.string.complaint_coordinates_not_available)
        }
    }

    // This decides the complaint department either from stored departmentId or from issue type text.
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

    // This converts department text into the app's standard department names.
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

    // This returns the translated department name for the current app language.
    fun localizedDepartmentName(context: Context, value: String): String {
        return when (normalizeDepartment(value).orEmpty()) {
            "Water" -> context.getString(R.string.department_water)
            "Roads" -> context.getString(R.string.department_roads)
            "Sanitation" -> context.getString(R.string.department_sanitation)
            "Electricity" -> context.getString(R.string.department_electricity)
            "General" -> context.getString(R.string.department_general)
            "All Departments" -> context.getString(R.string.department_all)
            else -> value.ifBlank { context.getString(R.string.department_general) }
        }
    }
}
