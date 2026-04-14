package com.example.urban.bottomNavigation.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.bottomNavigation.complaint.ComplaintEtaManager
import com.example.urban.bottomNavigation.complaint.ComplaintFragment
import com.example.urban.bottomNavigation.complaint.ComplaintSnapshotParser
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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalTime
import java.time.ZoneId

class HomeFragment : Fragment() {

    private lateinit var loadingContainer: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var contentLayout: ScrollView
    private lateinit var chartSection: View
    private lateinit var emptyStateCard: View
    private lateinit var recentActivityCard: View
    private lateinit var recentActivityContainer: LinearLayout

    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var lineChart: LineChart

    private lateinit var tvGreeting: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvScopeTag: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvRangeCaption: TextView
    private lateinit var tvTrendSubtitle: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvResolved: TextView
    private lateinit var tvHighPriority: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvMonth: TextView
    private lateinit var tvResolutionRate: TextView
    private lateinit var tvTopDepartment: TextView
    private lateinit var tvNearBreachCount: TextView
    private lateinit var tvOverdueCount: TextView
    private lateinit var tvActiveOfficers: TextView
    private lateinit var tvOverloadedOfficers: TextView
    private lateinit var tvTopHandler: TextView
    private lateinit var tvTopIssueType: TextView
    private lateinit var tvAverageResolutionTime: TextView
    private lateinit var tvFastestDepartment: TextView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyBody: TextView
    private lateinit var tvRecentActivityEmpty: TextView

    private lateinit var btnRefreshDashboard: MaterialButton
    private lateinit var btnShareSummary: MaterialButton

    private lateinit var cardOverview: View
    private lateinit var cardPending: View
    private lateinit var cardProgress: View
    private lateinit var cardResolved: View
    private lateinit var cardHighPriority: View
    private lateinit var cardToday: View
    private lateinit var cardMonth: View

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val complaintsRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Complaints")
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private var selectedRange = DashboardRange.ALL_TIME
    private var currentRole = ""
    private var currentDepartment = ""
    private var currentName = ""
    private var currentScopeLabel = "Dashboard"
    private var officerDirectory: Map<String, String> = emptyMap()
    private var allVisibleComplaints: List<Complaint> = emptyList()
    private var currentMetrics: DashboardMetrics? = null
    private var currentDisplayRange = DashboardRange.THIS_MONTH
    private var currentBarDepartments: List<String> = emptyList()
    private var complaintsListener: ValueEventListener? = null

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
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        contentLayout = view.findViewById(R.id.contentLayout)
        chartSection = view.findViewById(R.id.chartSection)
        emptyStateCard = view.findViewById(R.id.emptyStateCard)
        recentActivityCard = view.findViewById(R.id.recentActivityCard)
        recentActivityContainer = view.findViewById(R.id.recentActivityContainer)

        pieChart = view.findViewById(R.id.pieChartStatus)
        barChart = view.findViewById(R.id.barChartDepartment)
        lineChart = view.findViewById(R.id.lineChartTrend)

        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvSubtitle = view.findViewById(R.id.tvSubtitle)
        tvScopeTag = view.findViewById(R.id.tvScopeTag)
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated)
        tvRangeCaption = view.findViewById(R.id.tvRangeCaption)
        tvTrendSubtitle = view.findViewById(R.id.tvTrendSubtitle)
        tvTotal = view.findViewById(R.id.tvTotal)
        tvPending = view.findViewById(R.id.tvPendingCount)
        tvProgress = view.findViewById(R.id.tvProgressCount)
        tvResolved = view.findViewById(R.id.tvResolvedCount)
        tvHighPriority = view.findViewById(R.id.tvHighPriorityCount)
        tvToday = view.findViewById(R.id.tvTodayCount)
        tvMonth = view.findViewById(R.id.tvMonthCount)
        tvResolutionRate = view.findViewById(R.id.tvResolutionRate)
        tvTopDepartment = view.findViewById(R.id.tvTopDepartment)
        tvNearBreachCount = view.findViewById(R.id.tvNearBreachCount)
        tvOverdueCount = view.findViewById(R.id.tvOverdueCount)
        tvActiveOfficers = view.findViewById(R.id.tvActiveOfficers)
        tvOverloadedOfficers = view.findViewById(R.id.tvOverloadedOfficers)
        tvTopHandler = view.findViewById(R.id.tvTopHandler)
        tvTopIssueType = view.findViewById(R.id.tvTopIssueType)
        tvAverageResolutionTime = view.findViewById(R.id.tvAverageResolutionTime)
        tvFastestDepartment = view.findViewById(R.id.tvFastestDepartment)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyBody = view.findViewById(R.id.tvEmptyBody)
        tvRecentActivityEmpty = view.findViewById(R.id.tvRecentActivityEmpty)

        btnRefreshDashboard = view.findViewById(R.id.btnRefreshDashboard)
        btnShareSummary = view.findViewById(R.id.btnShareSummary)

        cardOverview = view.findViewById(R.id.cardOverview)
        cardPending = view.findViewById(R.id.cardPending)
        cardProgress = view.findViewById(R.id.cardProgress)
        cardResolved = view.findViewById(R.id.cardResolved)
        cardHighPriority = view.findViewById(R.id.cardHighPriority)
        cardToday = view.findViewById(R.id.cardToday)
        cardMonth = view.findViewById(R.id.cardMonth)

        setupCharts()
        setupCardActions()
        setupActions()
        loadDashboardData(forceRefresh = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        complaintsListener?.let { complaintsRef.removeEventListener(it) }
        complaintsListener = null
    }

    private fun setupActions() {
        // Premium dashboard interactions: pull to refresh, quick refresh, and summary sharing.
        swipeRefreshLayout.setOnRefreshListener {
            loadDashboardData(forceRefresh = true)
        }
        btnRefreshDashboard.setOnClickListener {
            swipeRefreshLayout.isRefreshing = true
            loadDashboardData(forceRefresh = true)
        }
        btnShareSummary.setOnClickListener {
            shareDashboardSummary()
        }
    }

    // Load the current viewer context first, then attach the live complaints listener once.
    private fun loadDashboardData(forceRefresh: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (!forceRefresh) {
            showLoading(true)
        }

        database.child("Users")
            .child(uid)
            .get()
            .addOnSuccessListener { userSnapshot ->
                currentRole = userSnapshot.child("role").value?.toString().orEmpty()
                currentDepartment = userSnapshot.child("department").value?.toString().orEmpty()
                currentName = userSnapshot.child("name").value?.toString().orEmpty()
                currentScopeLabel = currentRole.ifBlank { "Dashboard" }

                bindHeader()

                loadOfficerDirectory {
                    attachComplaintsListener(uid)
                }
            }
            .addOnFailureListener {
                finishRefreshState()
                showLoading(false)
            }
    }

    private fun loadOfficerDirectory(onComplete: () -> Unit) {
        if (currentRole == "Field Officer") {
            officerDirectory = emptyMap()
            onComplete()
            return
        }

        database.child("Users")
            .orderByChild("role")
            .equalTo("Field Officer")
            .get()
            .addOnSuccessListener { snapshot ->
                officerDirectory = snapshot.children.associate { officer ->
                    officer.key.orEmpty() to officer.child("name").value?.toString().orEmpty().ifBlank { "Field Officer" }
                }
                onComplete()
            }
            .addOnFailureListener {
                officerDirectory = emptyMap()
                onComplete()
            }
    }

    private fun attachComplaintsListener(uid: String) {
        complaintsListener?.let { complaintsRef.removeEventListener(it) }

        complaintsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val visibleComplaints = ArrayList<Complaint>()

                for (item in snapshot.children) {
                    val complaint = ComplaintSnapshotParser.fromSnapshot(item) ?: continue
                    if (shouldIncludeComplaint(complaint, uid)) {
                        visibleComplaints.add(complaint)
                    }
                }

                allVisibleComplaints = visibleComplaints
                syncEtaForVisibleComplaints(visibleComplaints)
                bindMetrics()
                finishRefreshState()
                showLoading(false)
            }

            override fun onCancelled(error: DatabaseError) {
                finishRefreshState()
                showLoading(false)
            }
        }

        complaintsRef.addValueEventListener(complaintsListener as ValueEventListener)
    }

    private fun finishRefreshState() {
        swipeRefreshLayout.isRefreshing = false
    }

    private fun syncEtaForVisibleComplaints(complaints: List<Complaint>) {
        if (currentRole == "Field Officer") return

        val etaUpdates = ComplaintEtaManager.buildEtaUpdates(complaints)
        etaUpdates.forEach { (complaintKey, updates) ->
            complaintsRef.child(complaintKey).updateChildren(updates)
        }
    }

    private fun bindHeader() {
        val firstName = currentName.trim().split(" ").firstOrNull().orEmpty().ifBlank { getString(R.string.generic_user) }
        val greetingPrefix = when (LocalTime.now().hour) {
            in 5..11 -> getString(R.string.dashboard_greeting_morning)
            in 12..16 -> getString(R.string.dashboard_greeting_afternoon)
            in 17..20 -> getString(R.string.dashboard_greeting_evening)
            else -> getString(R.string.dashboard_greeting_welcome_back)
        }

        tvGreeting.text = getString(R.string.dashboard_greeting_with_name, greetingPrefix, firstName)
        tvScopeTag.text = localizedRoleLabel(currentScopeLabel)
        tvSubtitle.text = when (currentRole) {
            "Super Admin" -> getString(R.string.dashboard_subtitle_super_admin)
            "Department Admin" -> getString(
                R.string.dashboard_subtitle_department_admin,
                ComplaintDataFormatter.localizedDepartmentName(
                    requireContext(),
                    currentDepartment.ifBlank { "General" }
                )
            )
            "Field Officer" -> getString(R.string.dashboard_subtitle_field_officer)
            else -> getString(R.string.dashboard_subtitle_default)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        swipeRefreshLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun shouldIncludeComplaint(complaint: Complaint, uid: String): Boolean {
        return when (currentRole) {
            "Super Admin" -> true
            "Department Admin" -> {
                // Department admins only see complaints that resolve into their own service bucket.
                ComplaintDataFormatter.resolvedDepartment(complaint) ==
                    ComplaintDataFormatter.normalizeDepartment(currentDepartment)
            }
            "Field Officer" -> complaint.allottedOfficerId == uid
            else -> {
                // Fallback keeps the dashboard usable even if an older account has missing role text.
                true
            }
        }
    }

    // Rebuild every dashboard section from the same analytics snapshot so cards and charts stay in sync.
    private fun bindMetrics() {
        selectedRange = DashboardRange.ALL_TIME
        val metrics = DashboardAnalytics.buildMetrics(
            complaints = allVisibleComplaints,
            range = DashboardRange.ALL_TIME,
            zoneId = zoneId,
            officerDirectory = officerDirectory
        )
        currentDisplayRange = DashboardRange.ALL_TIME
        currentMetrics = metrics

        tvRangeCaption.text = getString(R.string.dashboard_range_caption_all)
        tvTrendSubtitle.text = when (currentDisplayRange) {
            DashboardRange.ALL_TIME -> getString(R.string.dashboard_trend_subtitle_all)
            DashboardRange.TODAY -> getString(R.string.dashboard_trend_subtitle_today)
            DashboardRange.SEVEN_DAYS -> getString(R.string.dashboard_trend_subtitle_seven_days)
            DashboardRange.THIRTY_DAYS -> getString(R.string.dashboard_trend_subtitle_thirty_days)
            DashboardRange.THIS_MONTH -> getString(R.string.dashboard_trend_subtitle_this_month)
        }

        tvTotal.text = metrics.total.toString()
        tvPending.text = metrics.pending.toString()
        tvProgress.text = metrics.progress.toString()
        tvResolved.text = metrics.resolved.toString()
        tvHighPriority.text = metrics.highPriority.toString()
        tvToday.text = metrics.todayCount.toString()
        tvMonth.text = metrics.monthCount.toString()
        tvResolutionRate.text = metrics.resolutionRate
        tvTopDepartment.text = ComplaintDataFormatter.localizedDepartmentName(requireContext(), metrics.topDepartment)
        tvNearBreachCount.text = getString(R.string.dashboard_near_breach_count, metrics.nearBreachCount)
        tvOverdueCount.text = getString(R.string.dashboard_overdue_count, metrics.overdueCount)
        tvActiveOfficers.text = getString(R.string.dashboard_active_officers, metrics.activeOfficers)
        tvOverloadedOfficers.text = getString(R.string.dashboard_overloaded_officers, metrics.overloadedOfficers)
        tvTopHandler.text = getString(R.string.dashboard_top_handler, metrics.topHandler)
        tvTopIssueType.text = metrics.topIssueType
        tvAverageResolutionTime.text = metrics.averageResolutionTime
        tvFastestDepartment.text = getString(
            R.string.dashboard_fastest_department,
            ComplaintDataFormatter.localizedDepartmentName(requireContext(), metrics.fastestDepartment)
        )
        tvLastUpdated.text = getString(R.string.dashboard_last_updated_all_data)

        // Keep the dashboard visible whenever complaint data exists, even if the chosen range is empty.
        val hasAnyComplaints = allVisibleComplaints.isNotEmpty()
        emptyStateCard.visibility = if (hasAnyComplaints) View.GONE else View.VISIBLE
        chartSection.visibility = View.VISIBLE
        recentActivityCard.visibility = View.VISIBLE

        if (!hasAnyComplaints) {
            tvEmptyTitle.text = when (currentRole) {
                "Department Admin" -> getString(R.string.dashboard_empty_title_department)
                "Field Officer" -> getString(R.string.dashboard_empty_title_field)
                else -> getString(R.string.dashboard_empty_title_default)
            }
            tvEmptyBody.text = when (currentRole) {
                "Department Admin" -> getString(R.string.dashboard_empty_body_department)
                "Field Officer" -> getString(R.string.dashboard_empty_body_field)
                else -> getString(R.string.dashboard_empty_body_default)
            }
            clearCharts()
            return
        }

        updatePieChart(metrics.statusCount)
        updateBarChart(metrics.departmentCount)
        updateLineChart(metrics.trendLabels, metrics.trendValues)
        bindRecentActivity(metrics.recentActivity)
    }

    private fun clearCharts() {
        pieChart.clear()
        barChart.clear()
        lineChart.clear()
        pieChart.invalidate()
        barChart.invalidate()
        lineChart.invalidate()
    }

    private fun setupCardActions() {
        // Summary cards behave like shortcuts into the complaints screen with pre-applied filters.
        cardOverview.setOnClickListener { openComplaints(range = currentDisplayRange.label) }
        cardPending.setOnClickListener { openComplaints(status = "Pending", range = currentDisplayRange.label) }
        cardProgress.setOnClickListener { openComplaints(status = "In Progress", range = currentDisplayRange.label) }
        cardResolved.setOnClickListener { openComplaints(status = "Resolved", range = currentDisplayRange.label) }
        cardHighPriority.setOnClickListener {
            openComplaints(priority = "High", sort = "High Priority First", range = currentDisplayRange.label)
        }
        cardToday.setOnClickListener { openComplaints(range = DashboardRange.TODAY.label) }
        cardMonth.setOnClickListener { openComplaints(range = DashboardRange.THIS_MONTH.label) }
    }

    private fun openComplaints(
        status: String? = null,
        priority: String? = null,
        department: String? = null,
        sort: String? = null,
        range: String? = null
    ) {
        parentFragmentManager.setFragmentResult(
            ComplaintFragment.REQUEST_KEY_FILTERS,
            ComplaintFragment.filterBundle(
                status = status,
                priority = priority,
                department = department,
                sort = sort,
                range = range
            )
        )

        requireActivity()
            .findViewById<BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.nav_complaints
    }

    private fun shareDashboardSummary() {
        val metrics = currentMetrics ?: return
        // Export/share summary keeps demo output quick without needing PDF generation.
        val shareText = DashboardAnalytics.shareSummaryText(
            metrics = metrics,
            range = currentDisplayRange,
            scopeLabel = currentScopeLabel
        )

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                getString(R.string.dashboard_share_summary_chooser)
            )
        )
    }

    private fun setupCharts() {
        // Pie-chart drill-down opens complaints by status.
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
        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val entry = e as? PieEntry ?: return
                openComplaints(status = entry.label, range = currentDisplayRange.label)
            }

            override fun onNothingSelected() = Unit
        })

        // Bar-chart drill-down opens complaints by department with shortened mobile labels.
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.setFitBars(true)
        barChart.setDrawGridBackground(false)
        barChart.setNoDataText(getString(R.string.dashboard_no_department_data))
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
        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val index = e?.x?.toInt() ?: return
                val department = currentBarDepartments.getOrNull(index) ?: return
                openComplaints(department = department, range = currentDisplayRange.label)
            }

            override fun onNothingSelected() = Unit
        })

        // Trend chart stays visual-only for now, while the other charts support drill-down.
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.setNoDataText(getString(R.string.dashboard_no_trend_data))
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

        val entries = data.filterValues { it > 0 }.map { PieEntry(it.value.toFloat(), it.key) }
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
        pieChart.centerText = getString(R.string.dashboard_status_overview)
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun updateBarChart(data: Map<String, Int>) {
        if (data.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        currentBarDepartments = data.keys.toList()
        val shortLabels = currentBarDepartments.map { DashboardAnalytics.departmentShortLabel(it) }
        val entries = currentBarDepartments.mapIndexed { index, label ->
            BarEntry(index.toFloat(), (data[label] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, getString(R.string.dashboard_departments)).apply {
            color = Color.parseColor("#3D8BFF")
            valueTextColor = Color.parseColor("#344054")
            valueTextSize = 11f
        }

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(shortLabels)
        barChart.data = BarData(dataSet).apply { barWidth = 0.55f }
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun updateLineChart(labels: List<String>, values: List<Int>) {
        if (labels.isEmpty() || values.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            return
        }

        val entries = values.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        val dataSet = LineDataSet(entries, getString(R.string.dashboard_complaint_activity)).apply {
            color = Color.parseColor("#5E35B1")
            lineWidth = 3f
            setCircleColor(Color.parseColor("#5E35B1"))
            circleRadius = 4f
            setDrawValues(false)
            fillAlpha = 30
            setDrawFilled(true)
            fillColor = Color.parseColor("#D9CCFF")
        }

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        lineChart.data = LineData(dataSet)
        lineChart.animateX(800)
        lineChart.invalidate()
    }

    private fun localizedRoleLabel(role: String): String {
        return when (role) {
            "Super Admin" -> getString(R.string.role_super_admin)
            "Department Admin" -> getString(R.string.role_department_admin)
            "Field Officer" -> getString(R.string.role_field_officer)
            else -> role.ifBlank { getString(R.string.toolbar_dashboard) }
        }
    }

    // Inflate the recent feed dynamically so it stays easy to adjust without introducing another adapter.
    private fun bindRecentActivity(items: List<DashboardActivityItem>) {
        recentActivityContainer.removeAllViews()
        tvRecentActivityEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        if (items.isEmpty()) return

        val inflater = LayoutInflater.from(requireContext())
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_dashboard_activity, recentActivityContainer, false)
            val titleView = row.findViewById<TextView>(R.id.tvActivityTitle)
            val metaView = row.findViewById<TextView>(R.id.tvActivityMeta)
            val statusView = row.findViewById<TextView>(R.id.tvActivityStatus)

            titleView.text = item.title
            metaView.text = "${item.departmentLabel} • ${item.timestampLabel}"
            statusView.text = item.statusLabel

            when (item.statusLabel) {
                "Pending" -> statusView.setBackgroundResource(R.drawable.status_pending)
                "In Progress" -> statusView.setBackgroundResource(R.drawable.status_progress)
                else -> statusView.setBackgroundResource(R.drawable.status_resolved)
            }

            recentActivityContainer.addView(row)
        }
    }
}
