package com.example.urban.bottomNavigation.home

import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.bottomNavigation.complaint.ComplaintEtaManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DashboardRange(val label: String) {
    ALL_TIME("All Data"),
    TODAY("Today"),
    SEVEN_DAYS("7 Days"),
    THIRTY_DAYS("30 Days"),
    THIS_MONTH("This Month")
}

data class DashboardActivityItem(
    val title: String,
    val statusLabel: String,
    val departmentLabel: String,
    val timestampLabel: String
)

data class DashboardMetrics(
    val scopedComplaints: List<Complaint>,
    val statusCount: Map<String, Int>,
    val departmentCount: Map<String, Int>,
    val total: Int,
    val pending: Int,
    val progress: Int,
    val resolved: Int,
    val highPriority: Int,
    val todayCount: Int,
    val monthCount: Int,
    val nearBreachCount: Int,
    val overdueCount: Int,
    val activeOfficers: Int,
    val overloadedOfficers: Int,
    val topHandler: String,
    val resolutionRate: String,
    val topDepartment: String,
    val topIssueType: String,
    val averageResolutionTime: String,
    val fastestDepartment: String,
    val trendLabels: List<String>,
    val trendValues: List<Int>,
    val recentActivity: List<DashboardActivityItem>
)

object DashboardAnalytics {

    private const val OVERLOAD_THRESHOLD = 4
    private val activityFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())

    // This builds one complete dashboard metrics object from the visible complaint list.
    fun buildMetrics(
        complaints: List<Complaint>,
        range: DashboardRange,
        zoneId: ZoneId,
        officerDirectory: Map<String, String>
    ): DashboardMetrics {
        val today = LocalDate.now(zoneId)
        val currentMonth = YearMonth.now(zoneId)
        val scopedComplaints = complaints.filter { complaint ->
            matchesRange(complaint, range, zoneId, today, currentMonth)
        }

        val statusCount = linkedMapOf(
            "Pending" to 0,
            "In Progress" to 0,
            "Resolved" to 0
        )
        val departmentCount = linkedMapOf<String, Int>()
        val issueTypeCount = linkedMapOf<String, Int>()
        val trendBuckets = buildTrendBuckets(range, zoneId, today)
        val openOfficerLoad = linkedMapOf<String, Int>()
        val resolvedDurations = mutableListOf<Long>()
        val departmentResolutionDurations = linkedMapOf<String, MutableList<Long>>()
        var highPriority = 0
        var todayCount = 0
        var monthCount = 0
        var nearBreachCount = 0
        var overdueCount = 0

        for (complaint in scopedComplaints) {
            when (complaint.status) {
                0 -> statusCount["Pending"] = (statusCount["Pending"] ?: 0) + 1
                1 -> statusCount["In Progress"] = (statusCount["In Progress"] ?: 0) + 1
                2 -> statusCount["Resolved"] = (statusCount["Resolved"] ?: 0) + 1
            }

            if (complaint.priority == 2) {
                highPriority++
            }

            val department = ComplaintDataFormatter.resolvedDepartment(complaint)
            departmentCount[department] = (departmentCount[department] ?: 0) + 1

            val issueType = complaint.issueType.ifBlank { "General" }
            issueTypeCount[issueType] = (issueTypeCount[issueType] ?: 0) + 1

            complaint.timestamp.toLocalDate(zoneId)?.let { complaintDate ->
                if (complaintDate == today) {
                    todayCount++
                }
                if (YearMonth.from(complaintDate) == currentMonth) {
                    monthCount++
                }
            }

            assignTrendBucket(complaint, trendBuckets, range, zoneId)

            val openComplaint = complaint.status != 2 && complaint.allottedOfficerId.isNotBlank()
            if (openComplaint) {
                // Officer workload snapshot is based on live open assignments only.
                openOfficerLoad[complaint.allottedOfficerId] = (openOfficerLoad[complaint.allottedOfficerId] ?: 0) + 1
            }

            // Real SLA tracking uses department-specific time limits from the project scope.
            calculateSlaState(complaint, zoneId)?.let { state ->
                if (state == SlaState.NEAR_BREACH) nearBreachCount++
                if (state == SlaState.OVERDUE) overdueCount++
            }

            // Resolution-time metrics feed the premium admin insight cards.
            val resolutionDuration = resolutionDurationHours(complaint)
            if (resolutionDuration != null) {
                resolvedDurations.add(resolutionDuration)
                departmentResolutionDurations
                    .getOrPut(department) { mutableListOf() }
                    .add(resolutionDuration)
            }
        }

        val topHandler = openOfficerLoad.maxByOrNull { it.value }?.let { (uid, count) ->
            "${officerDirectory[uid] ?: "Field Officer"} ($count)"
        } ?: "No active assignments"

        val averageResolutionTime = if (resolvedDurations.isEmpty()) {
            "No resolved data"
        } else {
            formatDurationHours(resolvedDurations.average())
        }

        val fastestDepartment = departmentResolutionDurations
            .mapValues { (_, values) -> values.average() }
            .minByOrNull { it.value }
            ?.key ?: "No resolved data"

        val recentActivity = scopedComplaints
            .sortedByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.timestamp }
            .take(5)
            .map { complaint ->
                // Recent activity keeps the dashboard feeling operational instead of chart-only.
                DashboardActivityItem(
                    title = complaint.title.ifBlank { complaint.issueType.ifBlank { "Complaint update" } },
                    statusLabel = statusLabel(complaint.status),
                    departmentLabel = ComplaintDataFormatter.resolvedDepartment(complaint),
                    timestampLabel = formatActivityTime(
                        complaint.updatedAt.takeIf { it > 0L } ?: complaint.timestamp,
                        zoneId
                    )
                )
            }

        val trendLabels = trendBuckets.map { it.label }
        val trendValues = trendBuckets.map { it.count }

        return DashboardMetrics(
            scopedComplaints = scopedComplaints,
            statusCount = statusCount,
            departmentCount = departmentCount,
            total = scopedComplaints.size,
            pending = statusCount["Pending"] ?: 0,
            progress = statusCount["In Progress"] ?: 0,
            resolved = statusCount["Resolved"] ?: 0,
            highPriority = highPriority,
            todayCount = todayCount,
            monthCount = monthCount,
            nearBreachCount = nearBreachCount,
            overdueCount = overdueCount,
            activeOfficers = openOfficerLoad.size,
            overloadedOfficers = openOfficerLoad.count { it.value >= OVERLOAD_THRESHOLD },
            topHandler = topHandler,
            resolutionRate = if (scopedComplaints.isEmpty()) "0%" else "${((statusCount["Resolved"] ?: 0) * 100) / scopedComplaints.size}%",
            topDepartment = departmentCount.maxByOrNull { it.value }?.key ?: "No data",
            topIssueType = issueTypeCount.maxByOrNull { it.value }?.key ?: "No data",
            averageResolutionTime = averageResolutionTime,
            fastestDepartment = fastestDepartment,
            trendLabels = trendLabels,
            trendValues = trendValues,
            recentActivity = recentActivity
        )
    }

    // This converts the stored complaint status into a readable dashboard label.
    fun statusLabel(status: Int): String {
        return when (status) {
            0 -> "Pending"
            1 -> "In Progress"
            2 -> "Resolved"
            else -> "Unknown"
        }
    }

    // This shortens long department names so the department chart stays readable on small screens.
    fun departmentShortLabel(value: String): String {
        return when (value) {
            "Sanitation" -> "Sanit."
            "Electricity" -> "Electric."
            else -> value
        }
    }

    // This creates the plain text summary used when the dashboard is shared.
    fun shareSummaryText(
        metrics: DashboardMetrics,
        range: DashboardRange,
        scopeLabel: String
    ): String {
        return buildString {
            appendLine("Urban Fix Dashboard Summary")
            appendLine("Scope: $scopeLabel")
            appendLine("Range: ${range.label}")
            appendLine("Total: ${metrics.total}")
            appendLine("Pending: ${metrics.pending}")
            appendLine("In Progress: ${metrics.progress}")
            appendLine("Resolved: ${metrics.resolved}")
            appendLine("High Priority: ${metrics.highPriority}")
            appendLine("Near Breach: ${metrics.nearBreachCount}")
            appendLine("Overdue: ${metrics.overdueCount}")
            appendLine("Resolution Rate: ${metrics.resolutionRate}")
            appendLine("Top Department: ${metrics.topDepartment}")
            appendLine("Top Issue Type: ${metrics.topIssueType}")
            appendLine("Average Resolution Time: ${metrics.averageResolutionTime}")
            appendLine("Fastest Department: ${metrics.fastestDepartment}")
            appendLine("Top Handler: ${metrics.topHandler}")
        }
    }

    private enum class SlaState { NEAR_BREACH, OVERDUE }

    private data class TrendBucket(
        val label: String,
        val startMillis: Long,
        val endMillis: Long,
        var count: Int = 0
    )

    // This builds the time buckets used by the dashboard trend chart.
    private fun buildTrendBuckets(range: DashboardRange, zoneId: ZoneId, today: LocalDate): List<TrendBucket> {
        return when (range) {
            DashboardRange.ALL_TIME -> {
                val anchor = complaintsAnchorDate(today, zoneId)
                (5 downTo 0).map { offset ->
                    val startDate = anchor.minusDays((offset * 30).toLong())
                    val endDate = startDate.plusDays(30)
                    val start = startDate.atStartOfDay(zoneId)
                    TrendBucket(
                        label = startDate.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())),
                        startMillis = start.toInstant().toEpochMilli(),
                        endMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    )
                }
            }
            DashboardRange.TODAY -> {
                val dayStart = today.atStartOfDay(zoneId)
                (0 until 6).map { index ->
                    val start = dayStart.plusHours((index * 4).toLong())
                    val end = start.plusHours(4)
                    TrendBucket(
                        label = start.toLocalTime().toString().take(5),
                        startMillis = start.toInstant().toEpochMilli(),
                        endMillis = end.toInstant().toEpochMilli()
                    )
                }
            }
            DashboardRange.SEVEN_DAYS -> (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                val start = date.atStartOfDay(zoneId)
                TrendBucket(
                    label = date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())),
                    startMillis = start.toInstant().toEpochMilli(),
                    endMillis = start.plusDays(1).toInstant().toEpochMilli()
                )
            }
            DashboardRange.THIRTY_DAYS -> (29 downTo 0 step 5).map { offset ->
                val startDate = today.minusDays(offset.toLong())
                val endDate = startDate.plusDays(5)
                val start = startDate.atStartOfDay(zoneId)
                TrendBucket(
                    label = "${startDate.dayOfMonth}-${endDate.minusDays(1).dayOfMonth}",
                    startMillis = start.toInstant().toEpochMilli(),
                    endMillis = start.plusDays(5).toInstant().toEpochMilli()
                )
            }
            DashboardRange.THIS_MONTH -> {
                val month = YearMonth.from(today)
                val weekStarts = mutableListOf<LocalDate>()
                var current = month.atDay(1)
                while (current.month == month.month) {
                    weekStarts.add(current)
                    current = current.plusDays(7)
                }
                weekStarts.map { startDate ->
                    val start = startDate.atStartOfDay(zoneId)
                    val endDate = minOf(startDate.plusDays(7), month.atEndOfMonth().plusDays(1))
                    TrendBucket(
                        label = "W${weekStarts.indexOf(startDate) + 1}",
                        startMillis = start.toInstant().toEpochMilli(),
                        endMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    )
                }
            }
        }
    }

    // This places one complaint into the correct trend bucket for the current dashboard range.
    private fun assignTrendBucket(
        complaint: Complaint,
        buckets: List<TrendBucket>,
        range: DashboardRange,
        zoneId: ZoneId
    ) {
        val eventTime = complaint.updatedAt.takeIf { it > 0L } ?: complaint.timestamp
        if (eventTime <= 0L) return

        when (range) {
            DashboardRange.THIRTY_DAYS -> {
                val bucket = buckets.firstOrNull { eventTime in it.startMillis until it.endMillis }
                bucket?.count = (bucket?.count ?: 0) + 1
            }
            else -> {
                val bucket = buckets.firstOrNull { eventTime in it.startMillis until it.endMillis }
                bucket?.count = (bucket?.count ?: 0) + 1
            }
        }
    }

    // This checks whether a complaint belongs inside the selected dashboard date range.
    private fun matchesRange(
        complaint: Complaint,
        range: DashboardRange,
        zoneId: ZoneId,
        today: LocalDate,
        currentMonth: YearMonth
    ): Boolean {
        if (range == DashboardRange.ALL_TIME) {
            return true
        }

        val complaintDate = complaint.timestamp.toLocalDate(zoneId) ?: return false
        return when (range) {
            DashboardRange.ALL_TIME -> true
            DashboardRange.TODAY -> complaintDate == today
            DashboardRange.SEVEN_DAYS -> !complaintDate.isBefore(today.minusDays(6))
            DashboardRange.THIRTY_DAYS -> !complaintDate.isBefore(today.minusDays(29))
            DashboardRange.THIS_MONTH -> YearMonth.from(complaintDate) == currentMonth
        }
    }

    // This provides a fallback anchor date for long-range trend buckets.
    private fun complaintsAnchorDate(today: LocalDate, zoneId: ZoneId): LocalDate {
        return today.plusDays(1)
    }

    // This checks whether the complaint is near SLA breach or already overdue.
    private fun calculateSlaState(complaint: Complaint, zoneId: ZoneId): SlaState? {
        return when {
            ComplaintEtaManager.isOverdue(complaint) -> SlaState.OVERDUE
            ComplaintEtaManager.isNearBreach(complaint) -> SlaState.NEAR_BREACH
            else -> null
        }
    }

    // This returns complaint resolution time in hours when the complaint is already resolved.
    private fun resolutionDurationHours(complaint: Complaint): Long? {
        if (complaint.status != 2 || complaint.timestamp <= 0L || complaint.resolvedAt <= 0L) return null
        val diff = complaint.resolvedAt - complaint.timestamp
        if (diff <= 0L) return null
        return diff / (1000 * 60 * 60)
    }

    // This formats average hours into a friendlier admin-readable duration text.
    private fun formatDurationHours(hours: Double): String {
        return when {
            hours >= 48 -> String.format(Locale.getDefault(), "%.1f days", hours / 24.0)
            hours >= 1 -> String.format(Locale.getDefault(), "%.1f hrs", hours)
            else -> "<1 hr"
        }
    }

    // This converts a complaint timestamp into the short recent-activity label used on the dashboard.
    private fun formatActivityTime(timestamp: Long, zoneId: ZoneId): String {
        if (timestamp <= 0L) return "No time"
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId).format(activityFormatter)
    }

    // This converts epoch milliseconds into LocalDate for date comparison.
    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate? {
        if (this <= 0L) return null
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }
}
