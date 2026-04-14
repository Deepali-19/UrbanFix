package com.example.urban.bottomNavigation.complaint

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

data class ComplaintEtaPayload(
    val etaHours: Int,
    val estimatedResolutionAt: Long,
    val etaUpdatedAt: Long,
    val etaReason: String,
    val slaDeadline: Long
)

object ComplaintEtaManager {

    private const val HOUR_MILLIS = 60 * 60 * 1000L

    fun buildEtaUpdates(complaints: List<Complaint>, now: Long = System.currentTimeMillis()): Map<String, Map<String, Any>> {
        val openDepartmentLoad = complaints
            .filter { it.status != 2 }
            .groupingBy { ComplaintDataFormatter.resolvedDepartment(it) }
            .eachCount()

        val updates = linkedMapOf<String, Map<String, Any>>()

        complaints.forEach { complaint ->
            val complaintKey = complaint.firebaseKey.ifBlank { return@forEach }
            val payload = calculateEta(complaint, openDepartmentLoad[ComplaintDataFormatter.resolvedDepartment(complaint)] ?: 1, now)
                ?: return@forEach

            if (shouldPersist(complaint, payload)) {
                updates[complaintKey] = mapOf(
                    "etaHours" to payload.etaHours,
                    "estimatedResolutionAt" to payload.estimatedResolutionAt,
                    "etaUpdatedAt" to payload.etaUpdatedAt,
                    "etaReason" to payload.etaReason
                )
            }
        }

        return updates
    }

    fun calculateEta(complaint: Complaint, departmentOpenLoad: Int, now: Long = System.currentTimeMillis()): ComplaintEtaPayload? {
        if (complaint.status == 2) return null

        val department = ComplaintDataFormatter.resolvedDepartment(complaint)
        val baseHours = slaHoursFor(department)
        val loadFactor = when {
            departmentOpenLoad <= 4 -> 1.0
            departmentOpenLoad <= 8 -> 1.15
            departmentOpenLoad <= 14 -> 1.35
            else -> 1.6
        }
        val priorityFactor = when (complaint.priority) {
            2 -> 0.75
            1 -> 0.9
            else -> 1.0
        }
        val statusFactor = when (complaint.status) {
            1 -> 0.75
            else -> 1.0
        }

        val etaHours = ceil(baseHours * loadFactor * priorityFactor * statusFactor)
            .toInt()
            .coerceAtLeast(minEtaHoursFor(department))

        val referenceTimestamp = now
        val slaDeadline = complaint.timestamp.takeIf { it > 0L }?.plus((baseHours * HOUR_MILLIS).toLong())
            ?: referenceTimestamp + (baseHours * HOUR_MILLIS).toLong()

        return ComplaintEtaPayload(
            etaHours = etaHours,
            estimatedResolutionAt = referenceTimestamp + etaHours * HOUR_MILLIS,
            etaUpdatedAt = referenceTimestamp,
            etaReason = buildEtaReason(department, baseHours.toInt(), departmentOpenLoad, complaint.priority, complaint.status),
            slaDeadline = slaDeadline
        )
    }

    fun shouldPersist(complaint: Complaint, payload: ComplaintEtaPayload): Boolean {
        if (complaint.status == 2) return false

        if (complaint.estimatedResolutionAt <= 0L || complaint.etaHours <= 0 || complaint.etaUpdatedAt <= 0L) {
            return true
        }

        return complaint.etaHours != payload.etaHours ||
            abs(complaint.estimatedResolutionAt - payload.estimatedResolutionAt) >= HOUR_MILLIS ||
            complaint.etaReason != payload.etaReason
    }

    fun etaDisplayLabel(complaint: Complaint): String {
        return when {
            complaint.status == 2 -> "Resolved"
            complaint.estimatedResolutionAt > 0L -> {
                val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                "Expected by ${formatter.format(Date(complaint.estimatedResolutionAt))}"
            }
            complaint.etaHours > 0 -> "Expected within ${complaint.etaHours} hrs"
            else -> "ETA pending"
        }
    }

    fun etaShortLabel(complaint: Complaint): String {
        return when {
            complaint.status == 2 -> "Resolved"
            complaint.etaHours > 0 -> "ETA ${complaint.etaHours} hrs"
            else -> "ETA pending"
        }
    }

    fun etaReasonLabel(complaint: Complaint): String {
        return complaint.etaReason.ifBlank { "ETA will update from complaint type and current load." }
    }

    fun civilianEtaMessage(complaint: Complaint, complaintKey: String): String {
        val complaintDisplayId = complaint.complaintId.ifBlank { complaintKey }
        return when {
            complaint.status == 2 -> "Your complaint $complaintDisplayId has been resolved."
            complaint.estimatedResolutionAt > 0L -> {
                val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                "Your complaint $complaintDisplayId is expected to be resolved by ${formatter.format(Date(complaint.estimatedResolutionAt))}."
            }
            complaint.etaHours > 0 -> "Your complaint $complaintDisplayId is expected to be resolved within ${complaint.etaHours} hours."
            else -> "Your complaint $complaintDisplayId is under review. ETA will be updated soon."
        }
    }

    fun isNearBreach(complaint: Complaint, now: Long = System.currentTimeMillis()): Boolean {
        if (complaint.status == 2) return false
        val deadline = slaDeadlineFor(complaint)
        if (deadline <= 0L) return false
        val nearBreachThreshold = deadline - (slaHoursFor(ComplaintDataFormatter.resolvedDepartment(complaint)) * 0.2 * HOUR_MILLIS)
        return now in nearBreachThreshold.toLong()..deadline
    }

    fun isOverdue(complaint: Complaint, now: Long = System.currentTimeMillis()): Boolean {
        if (complaint.status == 2) return false
        val deadline = slaDeadlineFor(complaint)
        return deadline > 0L && now > deadline
    }

    fun slaHoursFor(complaint: Complaint): Double = slaHoursFor(ComplaintDataFormatter.resolvedDepartment(complaint))

    private fun slaHoursFor(department: String): Double {
        return when (department) {
            "Sanitation" -> 24.0
            "Water" -> 48.0
            "Roads" -> 24.0 * 7
            "Electricity" -> 48.0
            else -> 72.0
        }
    }

    private fun slaDeadlineFor(complaint: Complaint): Long {
        if (complaint.timestamp <= 0L) return 0L
        return complaint.timestamp + (slaHoursFor(complaint) * HOUR_MILLIS).toLong()
    }

    private fun minEtaHoursFor(department: String): Int {
        return when (department) {
            "Sanitation" -> 12
            "Water", "Electricity" -> 18
            "Roads" -> 48
            else -> 24
        }
    }

    private fun buildEtaReason(
        department: String,
        baseHours: Int,
        departmentOpenLoad: Int,
        priority: Int,
        status: Int
    ): String {
        val priorityLabel = when (priority) {
            2 -> "High"
            1 -> "Medium"
            else -> "Low"
        }
        val statusLabel = when (status) {
            1 -> "In Progress"
            else -> "Pending"
        }

        return "Base $baseHours hrs for $department • load $departmentOpenLoad open • $priorityLabel priority • $statusLabel"
    }
}
