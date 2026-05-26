package com.example.urban.bottomNavigation.complaint

import com.google.firebase.database.DataSnapshot
import com.example.urban.loginSingUp.AccountApprovalManager
import java.util.Locale

object ComplaintSnapshotParser {

    // This creates a Complaint object from Firebase data, even when old records use mixed keys or types.
    fun fromSnapshot(snapshot: DataSnapshot): Complaint? {
        if (!snapshot.exists()) return null
        if (!isComplaintNode(snapshot)) return null

        val complaint = Complaint(
            complaintId = readString(snapshot, "complaintId", "complaintNo", "complaintNumber", "id").orEmpty(),
            issueType = readString(snapshot, "issueType", "category", "issue", "type").orEmpty(),
            status = readInt(snapshot, "status") ?: 0,
            title = readString(snapshot, "title", "subject", "headline").orEmpty(),
            description = readString(snapshot, "description", "details").orEmpty(),
            civilianId = readCivilianId(snapshot).orEmpty(),
            phone = readLong(snapshot, "phone", "mobile", "contact", "citizenPhone") ?: 0L,
            location = readLocation(snapshot).orEmpty(),
            latitude = readLatitude(snapshot) ?: 0.0,
            longitude = readLongitude(snapshot) ?: 0.0,
            allottedOfficerId = readAssignedOfficerId(snapshot).orEmpty(),
            feedback = readString(snapshot, "feedback", "remark", "remarks", "comment").orEmpty(),
            images = readComplaintImages(snapshot),
            timestamp = readLong(snapshot, "timestamp", "timeStamp", "createdAt", "reportedOn") ?: 0L,
            updatedAt = readLong(snapshot, "updatedAt") ?: 0L,
            resolvedAt = readLong(snapshot, "resolvedAt") ?: 0L,
            priority = readInt(snapshot, "priority") ?: 0,
            etaHours = readInt(snapshot, "etaHours") ?: 0,
            estimatedResolutionAt = readLong(snapshot, "estimatedResolutionAt") ?: 0L,
            etaUpdatedAt = readLong(snapshot, "etaUpdatedAt") ?: 0L,
            etaReason = readString(snapshot, "etaReason").orEmpty(),
            etaNotificationSentAt = readLong(snapshot, "etaNotificationSentAt") ?: 0L,
            aiSuggestion = readString(snapshot, "aiSuggestion").orEmpty(),
            aiSuggestionUpdatedAt = readLong(snapshot, "aiSuggestionUpdatedAt") ?: 0L,
            imageAiGeneratedScore = readDouble(snapshot, "imageAiGeneratedScore") ?: -1.0,
            imageAiCheckLabel = readString(snapshot, "imageAiCheckLabel").orEmpty(),
            imageAiCheckedAt = readLong(snapshot, "imageAiCheckedAt") ?: 0L,
            validation = readBoolean(snapshot, "validation") ?: false,
            readByAdmin = readBoolean(snapshot, "readByAdmin") ?: false,
            departmentId = readString(snapshot, "departmentId", "department", "dept", "departmentName", "selectedDepartment").orEmpty(),
            city = readCity(snapshot).orEmpty(),
            cityNormalized = readCityNormalized(snapshot).orEmpty()
        )

        complaint.firebaseKey = snapshot.key.orEmpty()
        complaint.firebasePath = snapshot.ref.path.toString()
        return complaint
    }

    // This reads the complaint city from direct fields first, then from nested location objects.
    private fun readCity(snapshot: DataSnapshot): String? {
        return readString(snapshot, "city", "cityName", "municipality", "district")
            ?: readNestedString(snapshot, "location", "city")
            ?: readNestedString(snapshot, "location", "district")
            ?: readNestedString(snapshot, "address", "city")
    }

    // This keeps city matching stable even if old records only store a human-readable city string.
    private fun readCityNormalized(snapshot: DataSnapshot): String? {
        val explicitCityNormalized = readString(snapshot, "cityNormalized")
        if (!explicitCityNormalized.isNullOrBlank()) {
            return AccountApprovalManager.normalizeCity(explicitCityNormalized)
        }

        val city = readCity(snapshot)
        return city?.takeIf { it.isNotBlank() }?.let(AccountApprovalManager::normalizeCity)
    }

    // This walks through flat or nested complaint trees and returns only real complaint nodes.
    fun complaintSnapshots(root: DataSnapshot): List<DataSnapshot> {
        val complaintNodes = mutableListOf<DataSnapshot>()
        collectComplaintSnapshots(root, complaintNodes)
        return complaintNodes
    }

    // This recursively scans the tree until it finds valid complaint records.
    private fun collectComplaintSnapshots(node: DataSnapshot, complaintNodes: MutableList<DataSnapshot>) {
        if (!node.exists()) return

        if (isComplaintNode(node)) {
            complaintNodes.add(node)
            return
        }

        node.children.forEach { child ->
            collectComplaintSnapshots(child, complaintNodes)
        }
    }

    // This checks whether the current snapshot itself is a complaint record or just a parent container.
    private fun isComplaintNode(snapshot: DataSnapshot): Boolean {
        val directKeys = listOf(
            "complaintId",
            "complaintNo",
            "complaintNumber",
            "id",
            "title",
            "subject",
            "headline",
            "description",
            "details",
            "issueType",
            "category",
            "issue",
            "type",
            "status",
            "civilianId",
            "userId",
            "citizenId",
            "uid",
            "ownerId",
            "reportedBy",
            "createdBy",
            "images",
            "image",
            "imageUrl",
            "uploadedImageUrl",
            "timestamp",
            "timeStamp"
        )

        return directKeys.any { snapshot.child(it).exists() }
    }

    // This reads the best available location text from the complaint snapshot.
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

    // This reads latitude from direct fields or nested coordinate objects.
    private fun readLatitude(snapshot: DataSnapshot): Double? {
        return readDouble(snapshot, "latitude", "lat")
            ?: readNestedDouble(snapshot, "location", "latitude")
            ?: readNestedDouble(snapshot, "coordinates", "latitude")
            ?: readNestedDouble(snapshot, "latLng", "latitude")
    }

    // This reads longitude from direct fields or nested coordinate objects.
    private fun readLongitude(snapshot: DataSnapshot): Double? {
        return readDouble(snapshot, "longitude", "lng", "lon")
            ?: readNestedDouble(snapshot, "location", "longitude")
            ?: readNestedDouble(snapshot, "coordinates", "longitude")
            ?: readNestedDouble(snapshot, "latLng", "longitude")
    }

    // This reads the first non-empty string from a list of possible Firebase keys.
    private fun readString(snapshot: DataSnapshot, vararg keys: String): String? {
        for (key in keys) {
            val value = snapshot.child(key).value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                return value
            }
        }
        return null
    }

    // This reads the complaint owner id from direct or nested keys.
    private fun readCivilianId(snapshot: DataSnapshot): String? {
        return readString(snapshot, "civilianId", "userId", "citizenId", "uid", "ownerId", "reportedBy", "createdBy")
            ?: readNestedString(snapshot, "user", "uid")
            ?: readNestedString(snapshot, "user", "id")
            ?: readNestedString(snapshot, "civilian", "uid")
            ?: readNestedString(snapshot, "civilian", "id")
            ?: readNestedString(snapshot, "citizen", "uid")
            ?: readNestedString(snapshot, "citizen", "id")
    }

    // This reads the assigned officer id from direct or nested keys.
    private fun readAssignedOfficerId(snapshot: DataSnapshot): String? {
        return readString(
            snapshot,
            "allottedOfficerId",
            "allotedOfficerId",
            "assignedOfficerId",
            "fieldOfficerId",
            "officerId",
            "assignedTo"
        )
            ?: readNestedString(snapshot, "assignedOfficer", "uid")
            ?: readNestedString(snapshot, "assignedOfficer", "id")
            ?: readNestedString(snapshot, "officer", "uid")
            ?: readNestedString(snapshot, "officer", "id")
    }

    // This reads a number as Long from a list of possible Firebase keys.
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

    // This reads a number as Int from a list of possible Firebase keys.
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

    // This reads a number as Double from a list of possible Firebase keys.
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

    // This reads a boolean from Firebase, including simple string and numeric fallback values.
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

    // This reads a nested string from an object like location.address.
    private fun readNestedString(snapshot: DataSnapshot, parentKey: String, childKey: String): String? {
        val parent = snapshot.child(parentKey)
        if (!parent.exists()) return null
        val value = parent.child(childKey).value?.toString()?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() && it.lowercase(Locale.getDefault()) != "null" }
    }

    // This reads a nested double from an object like coordinates.latitude.
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

    // This reads the image list safely from Firebase.
    private fun readStringList(node: DataSnapshot): ArrayList<String> {
        val items = arrayListOf<String>()
        node.children.forEach { child ->
            val value = child.value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                items.add(value)
            }
        }
        return items
    }

    // This reads complaint photos from list, single-value, or alternate image keys.
    private fun readComplaintImages(snapshot: DataSnapshot): ArrayList<String> {
        val imageNodes = listOf(
            "images",
            "imageUrls",
            "photoUrls",
            "mediaUrls",
            "photos",
            "media",
            "attachments"
        )

        imageNodes.forEach { key ->
            val node = snapshot.child(key)
            if (!node.exists()) return@forEach

            if (node.hasChildren()) {
                val items = readStringList(node)
                if (items.isNotEmpty()) return items

                val nestedItems = arrayListOf<String>()
                node.children.forEach { child ->
                    readImageValue(child)?.let(nestedItems::add)
                }
                if (nestedItems.isNotEmpty()) return nestedItems
            }

            readImageValue(node)?.let { return arrayListOf(it) }
        }

        val singleImage = readString(
            snapshot,
            "image",
            "imageUrl",
            "imageUri",
            "photo",
            "photoUrl",
            "mediaUrl",
            "uploadedImageUrl",
            "attachmentUrl",
            "fileUrl",
            "downloadUrl"
        )
        if (!singleImage.isNullOrBlank()) {
            return arrayListOf(singleImage)
        }

        return arrayListOf()
    }

    // This reads one image string from either a direct value or a nested object with url-like keys.
    private fun readImageValue(node: DataSnapshot): String? {
        val directValue = node.value?.toString()?.trim().orEmpty()
        if (directValue.isNotBlank() && directValue.lowercase(Locale.getDefault()) != "null" && !node.hasChildren()) {
            return directValue
        }

        val nestedKeys = listOf("url", "uri", "src", "imageUrl", "photoUrl", "fileUrl", "downloadUrl")
        nestedKeys.forEach { key ->
            val value = node.child(key).value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                return value
            }
        }

        return null
    }
}
