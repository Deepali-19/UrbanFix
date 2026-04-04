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
import com.facebook.shimmer.ShimmerFrameLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {

    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var contentLayout: ScrollView
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var lineChart: LineChart

    private lateinit var tvTotal: TextView

    private val database =
        FirebaseDatabase.getInstance().reference.child("Complaints")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shimmerLayout = view.findViewById(R.id.shimmerLayout)
        contentLayout = view.findViewById(R.id.contentLayout)
        shimmerLayout.startShimmer()

        pieChart = view.findViewById(R.id.pieChartStatus)
        barChart = view.findViewById(R.id.barChartDepartment)
        lineChart = view.findViewById(R.id.lineChartTrend)
        tvTotal = view.findViewById(R.id.tvTotal)

        loadComplaintData()
    }

    // ================= FIREBASE =================

    private fun loadComplaintData() {
        val ref = FirebaseDatabase.getInstance().getReference("Complaints")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE

                if (snapshot.childrenCount == 0L) {
                    pieChart.clear()
                    barChart.clear()
                    lineChart.clear()
                    tvTotal.text = "0"
                    return
                }

                tvTotal.text = snapshot.childrenCount.toString()

                val statusCount = HashMap<String, Int>()
                val departmentCount = HashMap<String, Int>()
                val dailyCount = HashMap<Int, Int>()

                for (item in snapshot.children) {

                    val status = item.child("status").getValue(Int::class.java) ?: 0
                    val department = item.child("departmentId").getValue(String::class.java) ?: "Unknown"
                    val timestamp = item.child("timestamp").getValue(Long::class.java)
                        ?: System.currentTimeMillis()

                    val statusLabel = when (status) {
                        0 -> "Pending"
                        1 -> "In Progress"
                        2 -> "Resolved"
                        else -> "Unknown"
                    }

                    statusCount[statusLabel] = (statusCount[statusLabel] ?: 0) + 1
                    departmentCount[department] = (departmentCount[department] ?: 0) + 1

                    val day = (timestamp / 86400000).toInt()
                    dailyCount[day] = (dailyCount[day] ?: 0) + 1
                }

                updatePieChart(statusCount)
                updateBarChart(departmentCount)
                updateLineChart(dailyCount)
            }

            override fun onCancelled(error: DatabaseError) {}
        })

//        val ref = FirebaseDatabase.getInstance().getReference("Complaints")
//
//        ref.addValueEventListener(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//
//                shimmerLayout.stopShimmer()
//                shimmerLayout.visibility = View.GONE
//                contentLayout.visibility = View.VISIBLE
//
//                tvTotal.text = snapshot.childrenCount.toString()
//
//                val statusCount = HashMap<String, Int>()
//                val departmentCount = HashMap<String, Int>()
//                val dailyCount = HashMap<Int, Int>()
//
//                for (item in snapshot.children) {
//
//
//                    val status = item.child("status").getValue(Int::class.java) ?: 0
//                    val department = item.child("departmentId").getValue(String::class.java) ?: continue
//                    val timestamp = item.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
//
//                    val statusLabel = when (status) {
//                        0 -> "Pending"
//                        1 -> "In Progress"
//                        2 -> "Resolved"
//                        else -> "Unknown"
//                    }
//                    android.util.Log.d("DASHBOARD", "Status: $statusLabel Department: $department")
//
//
//                    // STATUS PIE
//                    statusCount[statusLabel] = (statusCount[statusLabel] ?: 0) + 1
//
//                    // DEPARTMENT BAR
//                    departmentCount[department] = (departmentCount[department] ?: 0) + 1
//
//                    // DAILY LINE
//                    val day = (timestamp / 86400000).toInt()
//                    dailyCount[day] = (dailyCount[day] ?: 0) + 1
//
//
//                }
//
//                if (statusCount.isNotEmpty()) updatePieChart(statusCount)
//                if (departmentCount.isNotEmpty()) updateBarChart(departmentCount)
//                if (dailyCount.isNotEmpty()) updateLineChart(dailyCount)
//            }
//
//            override fun onCancelled(error: DatabaseError) {}
//        })
    }

    // ================= PIE =================

    private fun updatePieChart(data: Map<String, Int>) {

        val entries = data.map {
            PieEntry(it.value.toFloat(), it.key)
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()

        pieChart.data = PieData(dataSet)
        pieChart.description.isEnabled = false
        pieChart.animateY(800)
        pieChart.invalidate()

        pieChart.animateY(1000)
    }

    // ================= BAR =================

    private fun updateBarChart(data: Map<String, Int>) {

        val entries = ArrayList<BarEntry>()
        var index = 0f

        data.forEach { (_, value) ->
            entries.add(BarEntry(index++, value.toFloat()))
        }

        val dataSet = BarDataSet(entries, "Departments")
        dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()

        barChart.data = BarData(dataSet)
        barChart.description.isEnabled = false
        barChart.animateY(800)
        barChart.invalidate()

        barChart.animateY(1000)
    }

    // ================= LINE =================

    private fun updateLineChart(data: Map<Int, Int>) {

        val entries = data.entries
            .sortedBy { it.key }
            .map { Entry(it.key.toFloat(), it.value.toFloat()) }

        val dataSet = LineDataSet(entries, "Daily Complaints")
        dataSet.color = Color.BLUE
        dataSet.lineWidth = 3f
        dataSet.setCircleColor(Color.BLUE)

        lineChart.data = LineData(dataSet)
        lineChart.description.isEnabled = false
        lineChart.animateX(800)
        lineChart.invalidate()

        lineChart.animateX(1000)
    }
}