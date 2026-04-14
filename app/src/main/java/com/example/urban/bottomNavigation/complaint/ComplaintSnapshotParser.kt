package com.example.urban.bottomNavigation.complaint

import com.google.firebase.database.DataSnapshot
import java.util.Locale

object ComplaintSnapshotParser {

    fun fromSnapshot(snapshot: DataSnapshot): Complaint? {
        if (!snapshot.exists()) return null

        val complaint = Complaint(
            complaintId = readString(snapshot, "complaintId", "id").orEmpty(),
            issueType = readString(snapshot, "issueType", "category", "issue").orEmpty(),
            status = readInt(snapshot, "status") ?: 0,
            title = readString(snapshot, "title", "subject").orEmpty(),
            description = readString(snapshot, "description", "details").orEmpty(),
            civilianId = readString(snapshot, "civilianId", "userId", "citizenId").orEmpty(),
            phone = readLong(snapshot, "phone", "mobile", "contact", "citizenPhone") ?: 0L,
            location = readLocation(snapshot).orEmpty(),
            latitude = readLatitude(snapshot) ?: 0.0,
            longitude = readLongitude(snapshot) ?: 0.0,
            allottedOfficerId = readString(snapshot, "allottedOfficerId", "assignedOfficerId", "fieldOfficerId").orEmpty(),
            feedback = readString(snapshot, "feedback", "remark", "remarks", "comment").orEmpty(),
            images = readStringList(snapshot, "images"),
            timestamp = readLong(snapshot, "timestamp", "timeStamp", "createdAt", "reportedOn") ?: 0L,
            updatedAt = readLong(snapshot, "updatedAt") ?: 0L,
            resolvedAt = readLong(snapshot, "resolvedAt") ?: 0L,
            priority = readInt(snapshot, "priority") ?: 0,
            etaHours = readInt(snapshot, "etaHours") ?: 0,
            estimatedResolutionAt = readLong(snapshot, "estimatedResolutionAt") ?: 0L,
            etaUpdatedAt = readLong(snapshot, "etaUpdatedAt") ?: 0L,
            etaReason = readString(snapshot, "etaReason").orEmpty(),
            etaNotificationSentAt = readLong(snapshot, "etaNotificationSentAt") ?: 0L,
            validation = readBoolean(snapshot, "validation") ?: false,
            readByAdmin = readBoolean(snapshot, "readByAdmin") ?: false,
            departmentId = readString(snapshot, "departmentId", "department").orEmpty()
        )

        complaint.firebaseKey = snapshot.key.orEmpty()
        return complaint
    }

    private fun readLocation(snapshot: DataSnapshot): String? {
        val direct = readString(snapshot, "location", "address", "selectedLocation")
        if (!direct.isNullOrBlank()) return direct

        val locationNode = snapshot.child("location")
        if (!locationNode.exists()) return null

        return when (val raw = locationNode.value) {
            is String -> raw.trim().takeIf { it.isNotBlank() && it.lowercase(Locale.getDefault()) != "null" }
            else -> readNestedString(snapshot, "location", "address")
                ?: readNestedString(snapshot, "location", "name")
        }
    }

    private fun readLatitude(snapshot: DataSnapshot): Double? {
        return readDouble(snapshot, "latitude", "lat")
            ?: readNestedDouble(snapshot, "location", "latitude")
            ?: readNestedDouble(snapshot, "coordinates", "latitude")
            ?: readNestedDouble(snapshot, "latLng", "latitude")
    }

    private fun readLongitude(snapshot: DataSnapshot): Double? {
        return readDouble(snapshot, "longitude", "lng", "lon")
            ?: readNestedDouble(snapshot, "location", "longitude")
            ?: readNestedDouble(snapshot, "coordinates", "longitude")
            ?: readNestedDouble(snapshot, "latLng", "longitude")
    }

    private fun readString(snapshot: DataSnapshot, vararg keys: String): String? {
        for (key in keys) {
            val value = snapshot.child(key).value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                return value
            }
        }
        return null
    }

    private fun readLong(snapshot: DataSnapshot, vararg keys: String): Long? {
        for (key in keys) {
            val rawValue = snapshot.child(key).value ?: continue
            val parsed = when (rawValue) {
                is Long -> rawValue
                is Int -> rawValue.toLong()
                is Double -> rawValue.toLong()
                is String -> rawValue.trim().toLongOrNull()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun readInt(snapshot: DataSnapshot, vararg keys: String): Int? {
        for (key in keys) {
            val rawValue = snapshot.child(key).value ?: continue
            val parsed = when (rawValue) {
                is Int -> rawValue
                is Long -> rawValue.toInt()
                is Double -> rawValue.toInt()
                is String -> rawValue.trim().toIntOrNull()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun readDouble(snapshot: DataSnapshot, vararg keys: String): Double? {
        for (key in keys) {
            val rawValue = snapshot.child(key).value ?: continue
            val parsed = when (rawValue) {
                is Double -> rawValue
                is Long -> rawValue.toDouble()
                is Int -> rawValue.toDouble()
                is String -> rawValue.trim().toDoubleOrNull()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun readBoolean(snapshot: DataSnapshot, vararg keys: String): Boolean? {
        for (key in keys) {
            val rawValue = snapshot.child(key).value ?: continue
            val parsed = when (rawValue) {
                is Boolean -> rawValue
                is String -> rawValue.trim().lowercase(Locale.getDefault()).let { value ->
                    when (value) {
                        "true", "1", "yes" -> true
                        "false", "0", "no" -> false
                        else -> null
                    }
                }
                is Long -> rawValue != 0L
                is Int -> rawValue != 0
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun readNestedString(snapshot: DataSnapshot, parentKey: String, childKey: String): String? {
        val parent = snapshot.child(parentKey)
        if (!parent.exists()) return null
        val value = parent.child(childKey).value?.toString()?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() && it.lowercase(Locale.getDefault()) != "null" }
    }

    private fun readNestedDouble(snapshot: DataSnapshot, parentKey: String, childKey: String): Double? {
        val parent = snapshot.child(parentKey)
        if (!parent.exists()) return null
        val rawValue = parent.child(childKey).value ?: return null
        return when (rawValue) {
            is Double -> rawValue
            is Long -> rawValue.toDouble()
            is Int -> rawValue.toDouble()
            is String -> rawValue.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun readStringList(snapshot: DataSnapshot, key: String): ArrayList<String> {
        val node = snapshot.child(key)
        if (!node.exists()) return arrayListOf()

        val items = arrayListOf<String>()
        node.children.forEach { child ->
            val value = child.value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                items.add(value)
            }
        }
        return items
    }
}
