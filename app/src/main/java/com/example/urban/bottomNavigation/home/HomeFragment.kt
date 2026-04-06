package com.example.urban.bottomNavigation.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var loadingContainer: View
    private lateinit var contentLayout: ScrollView
    private lateinit var chartSection: View
    private lateinit var emptyStateCard: View

    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var lineChart: LineChart

    private lateinit var tvGreeting: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvScopeTag: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvResolved: TextView
    private lateinit var tvHighPriority: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvMonth: TextView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyBody: TextView

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val lineLabelFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingContainer = view.findViewById(R.id.homeLoadingContainer)
        contentLayout = view.findViewById(R.id.contentLayout)
        chartSection = view.findViewById(R.id.chartSection)
        emptyStateCard = view.findViewById(R.id.emptyStateCard)

        pieChart = view.findViewById(R.id.pieChartStatus)
        barChart = view.findViewById(R.id.barChartDepartment)
        lineChart = view.findViewById(R.id.lineChartTrend)

        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvSubtitle = view.findViewById(R.id.tvSubtitle)
        tvScopeTag = view.findViewById(R.id.tvScopeTag)
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated)
        tvTotal = view.findViewById(R.id.tvTotal)
        tvPending = view.findViewById(R.id.tvPendingCount)
        tvProgress = view.findViewById(R.id.tvProgressCount)
        tvResolved = view.findViewById(R.id.tvResolvedCount)
        tvHighPriority = view.findViewById(R.id.tvHighPriorityCount)
        tvToday = view.findViewById(R.id.tvTodayCount)
        tvMonth = view.findViewById(R.id.tvMonthCount)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyBody = view.findViewById(R.id.tvEmptyBody)

        setupCharts()
        loadComplaintData()
    }

    private fun loadComplaintData() {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        database.child("Users")
            .child(uid)
            .get()
            .addOnSuccessListener { userSnapshot ->
                val role = userSnapshot.child("role").value?.toString().orEmpty()
                val department = userSnapshot.child("department").value?.toString().orEmpty()
                val name = userSnapshot.child("name").value?.toString().orEmpty()

                bindHeader(name, role, department)

                database.child("Complaints")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            showLoading(false)

                            val visibleComplaints = ArrayList<Complaint>()

                            for (item in snapshot.children) {
                                val complaint = item.getValue(Complaint::class.java) ?: continue
                                if (shouldIncludeComplaint(complaint, role, department, uid)) {
                                    visibleComplaints.add(complaint)
                                }
                            }

                            bindDashboardData(visibleComplaints, role)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            showLoading(false)
                        }
                    })
            }
            .addOnFailureListener {
                showLoading(false)
            }
    }

    private fun bindHeader(name: String, role: String, department: String) {
        val firstName = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "User" }
        val greetingPrefix = when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Welcome back"
        }

        tvGreeting.text = "$greetingPrefix, $firstName"
        tvScopeTag.text = role.ifBlank { "Dashboard" }
        tvSubtitle.text = when (role) {
            "Super Admin" -> "City-wide complaint performance and activity overview."
            "Department Admin" -> "${department.ifBlank { "Department" }} complaints, priorities, and recent trend."
            "Field Officer" -> "Your assigned complaint activity and progress overview."
            else -> "Complaint activity overview."
        }
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun shouldIncludeComplaint(
        complaint: Complaint,
        role: String,
        department: String,
        uid: String
    ): Boolean {
        return when (role) {
            "Super Admin" -> true
            "Department Admin" -> {
                ComplaintDataFormatter.resolvedDepartment(complaint) == ComplaintDataFormatter.normalizeDepartment(department)
            }
            "Field Officer" -> complaint.allottedOfficerId == uid
            else -> false
        }
    }

    private fun bindDashboardData(complaints: List<Complaint>, role: String) {
        val today = LocalDate.now(zoneId)
        val currentMonth = YearMonth.now(zoneId)
        val recentDays = (6 downTo 0).map { today.minusDays(it.toLong()) }

        val statusCount = linkedMapOf(
            "Pending" to 0,
            "In Progress" to 0,
            "Resolved" to 0
        )
        val departmentCount = linkedMapOf<String, Int>()
        val dailyCount = linkedMapOf<LocalDate, Int>().apply {
            recentDays.forEach { put(it, 0) }
        }
        var highPriority = 0
        var todayCount = 0
        var monthCount = 0

        for (complaint in complaints) {
            when (complaint.status) {
                0 -> statusCount["Pending"] = (statusCount["Pending"] ?: 0) + 1
                1 -> statusCount["In Progress"] = (statusCount["In Progress"] ?: 0) + 1
                2 -> statusCount["Resolved"] = (statusCount["Resolved"] ?: 0) + 1
            }

            if (complaint.priority == 2) {
                highPriority++
            }

            val departmentName = ComplaintDataFormatter.resolvedDepartment(complaint)
            departmentCount[departmentName] = (departmentCount[departmentName] ?: 0) + 1

            val complaintDate = complaint.timestamp.toLocalDate()
            if (complaintDate != null) {
                if (complaintDate == today) {
                    todayCount++
                }
                if (YearMonth.from(complaintDate) == currentMonth) {
                    monthCount++
                }
                if (dailyCount.containsKey(complaintDate)) {
                    dailyCount[complaintDate] = (dailyCount[complaintDate] ?: 0) + 1
                }
            }
        }

        tvTotal.text = complaints.size.toString()
        tvPending.text = (statusCount["Pending"] ?: 0).toString()
        tvProgress.text = (statusCount["In Progress"] ?: 0).toString()
        tvResolved.text = (statusCount["Resolved"] ?: 0).toString()
        tvHighPriority.text = highPriority.toString()
        tvToday.text = todayCount.toString()
        tvMonth.text = monthCount.toString()
        tvLastUpdated.text = "Updated ${LocalDateTime.now(zoneId).format(timeFormatter)}"

        val hasComplaints = complaints.isNotEmpty()
        emptyStateCard.visibility = if (hasComplaints) View.GONE else View.VISIBLE
        chartSection.visibility = if (hasComplaints) View.VISIBLE else View.GONE

        if (!hasComplaints) {
            tvEmptyTitle.text = when (role) {
                "Department Admin" -> "No department complaints yet"
                "Field Officer" -> "No assigned complaints yet"
                else -> "No complaints yet"
            }
            tvEmptyBody.text = when (role) {
                "Department Admin" -> "Once complaints are assigned to your department, this dashboard will start showing live insights."
                "Field Officer" -> "Assigned complaints will appear here with status, priority, and progress trends."
                else -> "Once complaints are reported, this dashboard will show their live status and trends."
            }

            pieChart.clear()
            barChart.clear()
            lineChart.clear()
            pieChart.invalidate()
            barChart.invalidate()
            lineChart.invalidate()
            return
        }

        updatePieChart(statusCount.filterValues { it > 0 })
        updateBarChart(departmentCount.toList().sortedByDescending { it.second }.toMap())
        updateLineChart(dailyCount)
    }

    private fun setupCharts() {
        pieChart.setUsePercentValues(false)
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 62f
        pieChart.transparentCircleRadius = 66f
        pieChart.setEntryLabelColor(Color.TRANSPARENT)
        pieChart.setCenterTextColor(Color.parseColor("#111827"))
        pieChart.setCenterTextSize(15f)
        pieChart.legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#667085")
            textSize = 12f
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
        }

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.setFitBars(true)
        barChart.setDrawGridBackground(false)
        barChart.setNoDataText("No department data available")
        barChart.axisLeft.apply {
            axisMinimum = 0f
            textColor = Color.parseColor("#98A2B3")
            gridColor = Color.parseColor("#EEF2F6")
        }
        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            textColor = Color.parseColor("#98A2B3")
            setDrawGridLines(false)
            labelRotationAngle = -18f
        }

        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.setNoDataText("No trend data available")
        lineChart.axisLeft.apply {
            axisMinimum = 0f
            textColor = Color.parseColor("#98A2B3")
            gridColor = Color.parseColor("#EEF2F6")
        }
        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            textColor = Color.parseColor("#98A2B3")
            setDrawGridLines(false)
        }
    }

    private fun updatePieChart(data: Map<String, Int>) {
        if (data.isEmpty()) {
            pieChart.clear()
            pieChart.invalidate()
            return
        }

        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#F79009"),
                Color.parseColor("#3B82F6"),
                Color.parseColor("#12B76A")
            )
            valueTextColor = Color.parseColor("#111827")
            valueTextSize = 12f
            sliceSpace = 3f
        }

        pieChart.data = PieData(dataSet).apply {
            setValueTextColor(Color.parseColor("#111827"))
            setValueTextSize(12f)
        }
        pieChart.centerText = "Status\nOverview"
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun updateBarChart(data: Map<String, Int>) {
        if (data.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        val labels = data.keys.toList()
        val entries = labels.mapIndexed { index, label ->
            BarEntry(index.toFloat(), (data[label] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "Departments").apply {
            color = Color.parseColor("#3D8BFF")
            valueTextColor = Color.parseColor("#344054")
            valueTextSize = 11f
        }

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.data = BarData(dataSet).apply { barWidth = 0.55f }
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun updateLineChart(data: Map<LocalDate, Int>) {
        if (data.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            return
        }

        val labels = data.keys.toList()
        val entries = labels.mapIndexed { index, date ->
            Entry(index.toFloat(), (data[date] ?: 0).toFloat())
        }

        val dataSet = LineDataSet(entries, "Daily Complaints").apply {
            color = Color.parseColor("#5E35B1")
            lineWidth = 3f
            setCircleColor(Color.parseColor("#5E35B1"))
            circleRadius = 4f
            setDrawValues(false)
            fillAlpha = 30
            setDrawFilled(true)
            fillColor = Color.parseColor("#D9CCFF")
        }

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(
            labels.map { it.format(lineLabelFormatter) }
        )
        lineChart.data = LineData(dataSet)
        lineChart.animateX(800)
        lineChart.invalidate()
    }

    private fun Long.toLocalDate(): LocalDate? {
        if (this <= 0L) return null
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }
}
