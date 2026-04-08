package com.example.urban.bottomNavigation.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.bottomNavigation.complaint.ComplaintDetailFragment
import com.example.urban.bottomNavigation.complaint.ComplaintFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private enum class MapMode {
        DENSITY,
        DEPARTMENT,
        MARKERS
    }

    private data class MapCluster(
        val complaints: List<Complaint>,
        val center: LatLng,
        val dominantDepartment: String,
        val dominantIssueType: String,
        val dominantLocation: String
    )

    private lateinit var mapLoadingContainer: View
    private lateinit var scrollViewModes: HorizontalScrollView
    private lateinit var scrollViewDepartments: HorizontalScrollView
    private lateinit var scrollViewMapTypes: HorizontalScrollView
    private lateinit var mapOverlayContent: View
    private lateinit var chipGroupModes: ChipGroup
    private lateinit var chipGroupDepartments: ChipGroup
    private lateinit var chipGroupMapTypes: ChipGroup
    private lateinit var chipDensity: Chip
    private lateinit var chipDepartment: Chip
    private lateinit var chipMarkers: Chip
    private lateinit var chipRecent: Chip
    private lateinit var chipDepartmentAll: Chip
    private lateinit var chipDepartmentWater: Chip
    private lateinit var chipDepartmentRoads: Chip
    private lateinit var chipDepartmentSanitation: Chip
    private lateinit var chipDepartmentElectricity: Chip
    private lateinit var chipMapTypeNormal: Chip
    private lateinit var chipMapTypeSatellite: Chip
    private lateinit var chipMapTypeTerrain: Chip
    private lateinit var btnMySpot: MaterialButton
    private lateinit var btnCenterMap: MaterialButton
    private lateinit var tvMapTitle: TextView
    private lateinit var tvMapSubtitle: TextView
    private lateinit var tvCounterVisible: TextView
    private lateinit var tvCounterOverdue: TextView
    private lateinit var tvCounterHighPriority: TextView
    private lateinit var tvCounterUnresolved: TextView
    private lateinit var tvMapSummary: TextView
    private lateinit var tvMapMeta: TextView
    private lateinit var tvMapLegend: TextView

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var complaintsRef: DatabaseReference? = null
    private var complaintsListener: ValueEventListener? = null

    private var googleMap: GoogleMap? = null
    private var heatmapOverlay: TileOverlay? = null
    private var clusterManager: ClusterManager<ComplaintClusterItem>? = null
    private val hotspotCircles = mutableListOf<Circle>()
    private val hotspotMarkers = mutableListOf<Marker>()

    private val roleComplaints = mutableListOf<Complaint>()
    private var currentUid = ""
    private var currentRole = ""
    private var currentDepartment = ""
    private var currentMode = MapMode.DENSITY
    private var currentDepartmentFilter = "All Departments"
    private var isRecentOnly = false
    private var isMapReady = false
    private var isUserLoaded = false
    private var isComplaintsLoaded = false
    private var lastKnownUserLatLng: LatLng? = null
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableCurrentLocationLayer()
                centerOnUserLocation()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapLoadingContainer = view.findViewById(R.id.mapLoadingContainer)
        mapOverlayContent = view.findViewById(R.id.mapOverlayContent)
        scrollViewModes = view.findViewById(R.id.scrollViewModes)
        scrollViewDepartments = view.findViewById(R.id.scrollViewDepartments)
        scrollViewMapTypes = view.findViewById(R.id.scrollViewMapTypes)
        chipGroupModes = view.findViewById(R.id.chipGroupModes)
        chipGroupDepartments = view.findViewById(R.id.chipGroupDepartments)
        chipGroupMapTypes = view.findViewById(R.id.chipGroupMapTypes)
        chipDensity = view.findViewById(R.id.chipDensity)
        chipDepartment = view.findViewById(R.id.chipDepartment)
        chipMarkers = view.findViewById(R.id.chipMarkers)
        chipRecent = view.findViewById(R.id.chipRecent)
        chipDepartmentAll = view.findViewById(R.id.chipDepartmentAll)
        chipDepartmentWater = view.findViewById(R.id.chipDepartmentWater)
        chipDepartmentRoads = view.findViewById(R.id.chipDepartmentRoads)
        chipDepartmentSanitation = view.findViewById(R.id.chipDepartmentSanitation)
        chipDepartmentElectricity = view.findViewById(R.id.chipDepartmentElectricity)
        chipMapTypeNormal = view.findViewById(R.id.chipMapTypeNormal)
        chipMapTypeSatellite = view.findViewById(R.id.chipMapTypeSatellite)
        chipMapTypeTerrain = view.findViewById(R.id.chipMapTypeTerrain)
        btnMySpot = view.findViewById(R.id.btnMySpot)
        btnCenterMap = view.findViewById(R.id.btnCenterMap)
        tvMapTitle = view.findViewById(R.id.tvMapTitle)
        tvMapSubtitle = view.findViewById(R.id.tvMapSubtitle)
        tvCounterVisible = view.findViewById(R.id.tvCounterVisible)
        tvCounterOverdue = view.findViewById(R.id.tvCounterOverdue)
        tvCounterHighPriority = view.findViewById(R.id.tvCounterHighPriority)
        tvCounterUnresolved = view.findViewById(R.id.tvCounterUnresolved)
        tvMapSummary = view.findViewById(R.id.tvMapSummary)
        tvMapMeta = view.findViewById(R.id.tvMapMeta)
        tvMapLegend = view.findViewById(R.id.tvMapLegend)

        setupMapFragment()
        setupControls()
        loadCurrentUser()
    }

    private fun setupMapFragment() {
        val existingMapFragment =
            childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment

        val mapFragment = existingMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.mapContainer, it)
                .commit()
        }

        mapFragment.getMapAsync(this)
    }

    private fun setupControls() {
        chipGroupModes.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentMode = when (selectedId) {
                R.id.chipDensity -> MapMode.DENSITY
                R.id.chipMarkers -> MapMode.MARKERS
                else -> MapMode.DEPARTMENT
            }
            animateMapModeChange()
            renderMap()
        }

        chipGroupDepartments.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentDepartmentFilter = when (selectedId) {
                R.id.chipDepartmentWater -> "Water"
                R.id.chipDepartmentRoads -> "Roads"
                R.id.chipDepartmentSanitation -> "Sanitation"
                R.id.chipDepartmentElectricity -> "Electricity"
                else -> "All Departments"
            }
            renderMap()
        }

        chipGroupMapTypes.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            googleMap?.mapType = when (selectedId) {
                R.id.chipMapTypeSatellite -> GoogleMap.MAP_TYPE_SATELLITE
                R.id.chipMapTypeTerrain -> GoogleMap.MAP_TYPE_TERRAIN
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
        }

        chipRecent.setOnCheckedChangeListener { _, isChecked ->
            // Recent mode keeps the UI light by using a single compact toggle instead of more chips.
            isRecentOnly = isChecked
            renderMap()
        }

        // Keep recenter as a clear top-bar action instead of a floating map button.
        btnCenterMap.setOnClickListener {
            moveCameraToComplaints(currentVisibleComplaints())
        }

        // My Spot gives fast orientation on the map without adding another floating control.
        btnMySpot.setOnClickListener {
            centerOnUserLocation()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        googleMap?.mapType = when {
            chipMapTypeSatellite.isChecked -> GoogleMap.MAP_TYPE_SATELLITE
            chipMapTypeTerrain.isChecked -> GoogleMap.MAP_TYPE_TERRAIN
            else -> GoogleMap.MAP_TYPE_NORMAL
        }

        enableCurrentLocationLayer()
        renderMap()
    }

    private fun loadCurrentUser() {
        currentUid = auth.currentUser?.uid ?: run {
            isUserLoaded = true
            renderMap()
            return
        }

        database.child("Users").child(currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                currentRole = snapshot.child("role").value?.toString().orEmpty()
                currentDepartment = normalizeDepartment(snapshot.child("department").value?.toString().orEmpty())
                isUserLoaded = true
                configureRoleUi()
                listenToComplaints()
            }
            .addOnFailureListener {
                isUserLoaded = true
                configureRoleUi()
                renderMap()
            }
    }

    // Admin roles get analysis views, while field officers get direct operational markers only.
    private fun configureRoleUi() {
        when (currentRole) {
            "Field Officer" -> {
                currentMode = MapMode.MARKERS
                currentDepartmentFilter = "All Departments"
                chipMarkers.isChecked = true
                scrollViewModes.visibility = View.GONE
                scrollViewDepartments.visibility = View.GONE
                tvMapTitle.text = "Assigned Complaint Map"
                tvMapSubtitle.text = "Only your assigned complaints."
            }

            "Department Admin" -> {
                currentDepartmentFilter = if (isSupportedDepartment(currentDepartment)) {
                    currentDepartment
                } else {
                    "All Departments"
                }
                currentMode = MapMode.DENSITY
                chipDensity.isChecked = true
                checkDepartmentChip(currentDepartmentFilter)
                scrollViewModes.visibility = View.VISIBLE
                scrollViewDepartments.visibility = View.VISIBLE
                tvMapTitle.text = "Department Heat Map"
            }

            else -> {
                currentMode = MapMode.DENSITY
                currentDepartmentFilter = "All Departments"
                chipDensity.isChecked = true
                chipDepartmentAll.isChecked = true
                scrollViewModes.visibility = View.VISIBLE
                scrollViewDepartments.visibility = View.VISIBLE
                tvMapTitle.text = "Complaint Intelligence Map"
            }
        }
    }

    private fun listenToComplaints() {
        complaintsRef?.let { ref ->
            complaintsListener?.let(ref::removeEventListener)
        }

        complaintsRef = database.child("Complaints")
        complaintsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                roleComplaints.clear()

                for (child in snapshot.children) {
                    val complaint = child.getValue(Complaint::class.java) ?: continue
                    complaint.firebaseKey = child.key.orEmpty()

                    if (shouldIncludeForRole(complaint)) {
                        roleComplaints.add(complaint)
                    }
                }

                isComplaintsLoaded = true
                renderMap()
            }

            override fun onCancelled(error: DatabaseError) {
                isComplaintsLoaded = true
                renderMap()
            }
        }

        complaintsRef?.addValueEventListener(complaintsListener as ValueEventListener)
    }

    private fun shouldIncludeForRole(complaint: Complaint): Boolean {
        return when (currentRole) {
            "Super Admin" -> true
            "Department Admin" -> ComplaintDataFormatter.resolvedDepartment(complaint) == currentDepartment
            "Field Officer" -> complaint.allottedOfficerId == currentUid
            else -> false
        }
    }

    private fun renderMap() {
        if (!isMapReady || !isUserLoaded) return

        if (!isComplaintsLoaded) {
            mapLoadingContainer.visibility = View.VISIBLE
            return
        }

        mapLoadingContainer.visibility = View.GONE

        val filteredComplaints = currentVisibleComplaints()
        clearRenderedLayers()

        when {
            currentRole == "Field Officer" -> renderComplaintMarkers(filteredComplaints)
            currentMode == MapMode.DENSITY -> renderDensityHeatMap(filteredComplaints)
            currentMode == MapMode.DEPARTMENT -> renderDepartmentHotspots(filteredComplaints)
            else -> renderComplaintMarkers(filteredComplaints)
        }

        updateSummary(filteredComplaints)
        moveCameraToComplaints(filteredComplaints)
    }

    private fun currentVisibleComplaints(): List<Complaint> {
        val scopedComplaints = if (currentRole == "Field Officer") {
            roleComplaints
        } else {
            roleComplaints.filter { complaint ->
                currentDepartmentFilter == "All Departments" ||
                    ComplaintDataFormatter.resolvedDepartment(complaint) == currentDepartmentFilter
            }
        }

        return scopedComplaints.filter { complaint ->
            !isRecentOnly || isRecentComplaint(complaint)
        }
    }

    private fun isRecentComplaint(complaint: Complaint): Boolean {
        if (complaint.timestamp <= 0L) return false
        val age = System.currentTimeMillis() - complaint.timestamp
        return age <= RECENT_WINDOW_MS
    }

    private fun animateMapModeChange() {
        mapOverlayContent.animate()
            .alpha(0.9f)
            .setDuration(80L)
            .withEndAction {
                mapOverlayContent.animate()
                    .alpha(1f)
                    .setDuration(140L)
                    .start()
            }
            .start()
    }

    private fun enableCurrentLocationLayer() {
        val map = googleMap ?: return
        if (!hasFineLocationPermission()) return
        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) {
        }
    }

    private fun centerOnUserLocation() {
        if (!hasFineLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val latLng = location?.toLatLng()
                if (latLng != null) {
                    lastKnownUserLatLng = latLng
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(latLng)
                                .zoom(15f)
                                .build()
                        )
                    )
                } else {
                    moveCameraToComplaints(currentVisibleComplaints())
                }
            }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)

    private fun nearestComplaintTo(
        anchor: LatLng,
        complaints: List<Complaint>
    ): Complaint? {
        return complaints
            .filter(::hasValidCoordinate)
            .minByOrNull { complaint ->
                val latDistance = complaint.latitude - anchor.latitude
                val lngDistance = complaint.longitude - anchor.longitude
                sqrt(latDistance.pow(2) + lngDistance.pow(2))
            }
    }

    private fun clearRenderedLayers() {
        googleMap?.setOnMarkerClickListener(null)
        googleMap?.setOnCameraIdleListener(null)
        googleMap?.setOnInfoWindowClickListener(null)
        googleMap?.clear()

        clusterManager?.clearItems()
        clusterManager = null

        heatmapOverlay = null
        hotspotCircles.clear()
        hotspotMarkers.clear()
    }

    // Density mode highlights complaint pressure while still leaving hotspot pins clickable.
    private fun renderDensityHeatMap(complaints: List<Complaint>) {
        val heatPoints = complaints.weightedHeatPoints()
        if (heatPoints.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(
                Color.parseColor("#34D399"),
                Color.parseColor("#F59E0B"),
                Color.parseColor("#DC2626")
            ),
            floatArrayOf(0.2f, 0.6f, 1f)
        )

        val provider = HeatmapTileProvider.Builder()
            .data(heatPoints)
            .gradient(gradient)
            .radius(HEATMAP_RADIUS)
            .opacity(0.8)
            .build()

        heatmapOverlay = googleMap?.addTileOverlay(
            TileOverlayOptions().tileProvider(provider)
        )

        val hotspotClusters = buildClusters(complaints)
        renderHotspotMarkers(hotspotClusters, useDepartmentColor = true, withAreaCircles = false)
    }

    // Department mode shows which service dominates each area instead of using a busy heat layer.
    private fun renderDepartmentHotspots(complaints: List<Complaint>) {
        val clusters = buildClusters(complaints)
        renderHotspotMarkers(clusters, useDepartmentColor = true, withAreaCircles = true)
    }

    private fun renderHotspotMarkers(
        clusters: List<MapCluster>,
        useDepartmentColor: Boolean,
        withAreaCircles: Boolean
    ) {
        val map = googleMap ?: return
        map.setOnMarkerClickListener { marker ->
            val cluster = marker.tag as? MapCluster ?: return@setOnMarkerClickListener false
            showSelectionSheet(cluster.complaints, cluster.center, cluster.dominantLocation)
            true
        }

        clusters.forEach { cluster ->
            val color = if (useDepartmentColor) {
                departmentColor(cluster.dominantDepartment)
            } else {
                Color.parseColor("#0B4CC2")
            }

            if (withAreaCircles) {
                hotspotCircles += map.addCircle(
                    CircleOptions()
                        .center(cluster.center)
                        .radius((220 + (cluster.complaints.size * 60)).toDouble())
                        .strokeWidth(2f)
                        .strokeColor(color)
                        .fillColor(withAlpha(color, 58))
                )
            }

            val overdueCount = cluster.complaints.count(::isOverdue)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(cluster.center)
                    .title("${cluster.complaints.size} complaints nearby")
                    .snippet(
                        "${cluster.dominantDepartment} hotspot • ${cluster.dominantIssueType}${if (overdueCount > 0) " • $overdueCount overdue" else ""}"
                    )
                    .icon(BitmapDescriptorFactory.defaultMarker(departmentHue(cluster.dominantDepartment)))
            )

            marker?.tag = cluster
            marker?.let(hotspotMarkers::add)
        }
    }

    private fun renderComplaintMarkers(complaints: List<Complaint>) {
        val map = googleMap ?: return
        val validComplaints = complaints.filter(::hasValidCoordinate)
        if (validComplaints.isEmpty()) return

        val manager = ClusterManager<ComplaintClusterItem>(requireContext(), map)
        clusterManager = manager

        manager.renderer = ComplaintClusterRenderer(map, manager)
        manager.setOnClusterClickListener { cluster ->
            showSelectionSheet(cluster.items.map { it.complaint }, cluster.position, dominantLocation(cluster.items.map { it.complaint }))
            true
        }
        manager.setOnClusterItemClickListener { item ->
            showSelectionSheet(listOf(item.complaint), item.position, ComplaintDataFormatter.locationLabel(item.complaint))
            true
        }

        map.setOnCameraIdleListener(manager)
        map.setOnMarkerClickListener(manager)
        map.setOnInfoWindowClickListener(manager)

        manager.addItems(validComplaints.map(::ComplaintClusterItem))
        manager.cluster()
    }

    private fun updateSummary(complaints: List<Complaint>) {
        val visibleCount = complaints.size
        val overdueCount = complaints.count(::isOverdue)
        val highPriorityCount = complaints.count { it.priority == 2 }
        val unresolvedCount = complaints.count { it.status != 2 }

        tvCounterVisible.text = "Visible $visibleCount"
        tvCounterOverdue.text = "Overdue $overdueCount"
        tvCounterHighPriority.text = "High $highPriorityCount"
        tvCounterUnresolved.text = "Open $unresolvedCount"

        if (complaints.isEmpty()) {
            tvMapSummary.text = when (currentRole) {
                "Field Officer" -> "No assigned complaints to map"
                "Department Admin" -> "No department complaints found on the map"
                else -> "No complaints available for this map view"
            }
            tvMapMeta.text = if (isRecentOnly) {
                "Try turning off Recent or wait for new complaints with coordinates."
            } else {
                "Try another filter or wait for complaints with coordinates."
            }
            tvMapLegend.text = when (currentMode) {
                MapMode.DENSITY -> "Heat appears once there are location points to analyze."
                MapMode.DEPARTMENT -> "Department colors appear once nearby points exist."
                MapMode.MARKERS -> "Markers show only complaints with coordinates."
            }
            return
        }

        val topIssue = topIssueType(complaints)
        val nearBreachCount = complaints.count(::isNearBreach)
        val mappedPoints = complaints.count(::hasValidCoordinate)
        val hotspotCount = buildClusters(complaints).size

        tvMapSummary.text = when (currentMode) {
            MapMode.DENSITY -> "$mappedPoints mapped complaints across $hotspotCount hotspots"
            MapMode.DEPARTMENT -> "$hotspotCount active service hotspots on the map"
            MapMode.MARKERS -> "$mappedPoints clustered complaint points on the map"
        }
        tvMapMeta.text = when (currentMode) {
            MapMode.DENSITY -> "Top issue: $topIssue. SLA watch: $overdueCount overdue, $nearBreachCount near breach."
            MapMode.DEPARTMENT -> "Dominant departments color each area. Lead issue: $topIssue."
            MapMode.MARKERS -> "Tap a cluster or pin for details, directions, and drill-down."
        }
        tvMapLegend.text = when (currentMode) {
            MapMode.DENSITY -> "Heat scale: green low pressure, orange medium, red highest complaint concentration"
            MapMode.DEPARTMENT -> "Department colors: Water blue, Roads orange, Sanitation green, Electricity yellow"
            MapMode.MARKERS -> "Markers open complaint details, directions, and filtered complaint lists"
        }
    }

    private fun moveCameraToComplaints(complaints: List<Complaint>) {
        val points = complaints.validMapPoints()
        val map = googleMap ?: return

        if (points.isEmpty()) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 4.8f))
            return
        }

        if (points.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
            return
        }

        val bounds = LatLngBounds.builder().apply {
            points.forEach(::include)
        }.build()

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
    }

    private fun showSelectionSheet(
        complaints: List<Complaint>,
        center: LatLng,
        locationHint: String
    ) {
        if (complaints.isEmpty()) return

        val dialog = BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_map_area_sheet, null, false)

        val tvSheetTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val tvSheetSubtitle = view.findViewById<TextView>(R.id.tvSheetSubtitle)
        val tvSheetTotal = view.findViewById<TextView>(R.id.tvSheetTotal)
        val tvSheetPending = view.findViewById<TextView>(R.id.tvSheetPending)
        val tvSheetProgress = view.findViewById<TextView>(R.id.tvSheetProgress)
        val tvSheetResolved = view.findViewById<TextView>(R.id.tvSheetResolved)
        val tvSheetDepartment = view.findViewById<TextView>(R.id.tvSheetDepartment)
        val tvSheetIssueType = view.findViewById<TextView>(R.id.tvSheetIssueType)
        val tvSheetSla = view.findViewById<TextView>(R.id.tvSheetSla)
        val tvSheetLocation = view.findViewById<TextView>(R.id.tvSheetLocation)
        val btnSheetDirections = view.findViewById<MaterialButton>(R.id.btnSheetDirections)
        val btnSheetList = view.findViewById<MaterialButton>(R.id.btnSheetList)
        val btnSheetDetail = view.findViewById<MaterialButton>(R.id.btnSheetDetail)

        val pendingCount = complaints.count { it.status == 0 }
        val progressCount = complaints.count { it.status == 1 }
        val resolvedCount = complaints.count { it.status == 2 }
        val dominantDepartment = dominantDepartment(complaints)
        val issueType = topIssueType(complaints)
        val overdueCount = complaints.count(::isOverdue)
        val nearBreachCount = complaints.count(::isNearBreach)
        val singleComplaint = complaints.singleOrNull()

        tvSheetTitle.text = if (singleComplaint != null) {
            singleComplaint.title.ifBlank { "Complaint Detail Shortcut" }
        } else {
            "${complaints.size} complaints in this area"
        }
        tvSheetSubtitle.text = if (singleComplaint != null) {
            "${dominantDepartment(singleComplaint)} • ${statusLabel(singleComplaint.status)}"
        } else {
            "Area intelligence, routes, and complaint drill-down"
        }
        tvSheetTotal.text = "Total: ${complaints.size}"
        tvSheetPending.text = "Pending: $pendingCount"
        tvSheetProgress.text = "In Progress: $progressCount"
        tvSheetResolved.text = "Resolved: $resolvedCount"
        tvSheetDepartment.text = "Top Department: $dominantDepartment"
        tvSheetIssueType.text = "Most repeated issue: $issueType"
        tvSheetSla.text = "SLA risk: $overdueCount overdue, $nearBreachCount nearing breach"
        tvSheetLocation.text = "Location: $locationHint"

        btnSheetDirections.text =
            if (currentRole == "Field Officer" && complaints.size > 1) "Next Route" else "Directions"
        btnSheetDirections.setOnClickListener {
            openRouteForSelection(complaints, center, locationHint)
        }

        btnSheetList.setOnClickListener {
            openComplaintsForSelection(complaints)
            dialog.dismiss()
        }

        if (singleComplaint == null) {
            btnSheetDetail.visibility = View.GONE
        } else {
            btnSheetDetail.visibility = View.VISIBLE
            btnSheetDetail.setOnClickListener {
                openComplaintDetail(singleComplaint)
                dialog.dismiss()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun openComplaintsForSelection(complaints: List<Complaint>) {
        val complaintKeys = ArrayList(
            complaints.mapNotNull { complaint ->
                complaint.firebaseKey.ifBlank { complaint.complaintId }.takeIf { it.isNotBlank() }
            }
        )

        parentFragmentManager.setFragmentResult(
            ComplaintFragment.REQUEST_KEY_FILTERS,
            ComplaintFragment.filterBundle(
                department = if (currentDepartmentFilter != "All Departments") currentDepartmentFilter else null,
                complaintKeys = complaintKeys
            )
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ComplaintFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openComplaintDetail(complaint: Complaint) {
        val complaintKey = complaint.firebaseKey.ifBlank { complaint.complaintId }
        if (complaintKey.isBlank()) return

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ComplaintDetailFragment.newInstance(complaintKey))
            .addToBackStack(null)
            .commit()
    }

    private fun openDirections(center: LatLng, label: String) {
        val uri = Uri.parse("geo:0,0?q=${center.latitude},${center.longitude}(${Uri.encode(label)})")
        val preferredIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)

        if (preferredIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(preferredIntent)
        } else {
            startActivity(fallbackIntent)
        }
    }

    private fun openRouteForSelection(
        complaints: List<Complaint>,
        fallbackCenter: LatLng,
        fallbackLabel: String
    ) {
        if (currentRole == "Field Officer" && complaints.size > 1) {
            val anchor = lastKnownUserLatLng
            val targetComplaint = nearestComplaintTo(anchor ?: fallbackCenter, complaints)
            if (targetComplaint != null) {
                openDirections(
                    LatLng(targetComplaint.latitude, targetComplaint.longitude),
                    targetComplaint.title.ifBlank { ComplaintDataFormatter.locationLabel(targetComplaint) }
                )
                return
            }
        }

        openDirections(fallbackCenter, fallbackLabel)
    }

    private fun buildClusters(complaints: List<Complaint>): List<MapCluster> {
        val groupedComplaints = complaints
            .filter(::hasValidCoordinate)
            .groupBy { complaint ->
                val latKey = (complaint.latitude / CLUSTER_CELL_SIZE).roundToInt()
                val lngKey = (complaint.longitude / CLUSTER_CELL_SIZE).roundToInt()
                latKey to lngKey
            }

        return groupedComplaints.values.map { clusterComplaints ->
            val avgLat = clusterComplaints.map { it.latitude }.average()
            val avgLng = clusterComplaints.map { it.longitude }.average()

            MapCluster(
                complaints = clusterComplaints,
                center = LatLng(avgLat, avgLng),
                dominantDepartment = dominantDepartment(clusterComplaints),
                dominantIssueType = topIssueType(clusterComplaints),
                dominantLocation = dominantLocation(clusterComplaints)
            )
        }
    }

    private fun dominantDepartment(complaints: List<Complaint>): String {
        return complaints
            .groupingBy { ComplaintDataFormatter.resolvedDepartment(it) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "General"
    }

    private fun dominantDepartment(complaint: Complaint): String =
        ComplaintDataFormatter.resolvedDepartment(complaint)

    private fun topIssueType(complaints: List<Complaint>): String {
        return complaints
            .groupingBy { it.issueType.ifBlank { "General complaint" } }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "General complaint"
    }

    private fun dominantLocation(complaints: List<Complaint>): String {
        val location = complaints
            .map { it.location.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return location ?: complaints.firstOrNull()?.let { ComplaintDataFormatter.coordinatesLabel(it) }
            ?: "Location not available"
    }

    private fun Complaint.weightForHeat(): Int {
        var weight = 1
        if (priority == 2) weight += 2
        if (priority == 1) weight += 1
        if (status != 2) weight += 1
        if (isNearBreach(this)) weight += 1
        if (isOverdue(this)) weight += 2
        return weight.coerceAtMost(6)
    }

    private fun List<Complaint>.weightedHeatPoints(): List<LatLng> {
        val points = mutableListOf<LatLng>()
        filter(::hasValidCoordinate).forEach { complaint ->
            repeat(complaint.weightForHeat()) {
                points += LatLng(complaint.latitude, complaint.longitude)
            }
        }
        return points
    }

    private fun checkDepartmentChip(value: String) {
        when (value) {
            "Water" -> chipDepartmentWater.isChecked = true
            "Roads" -> chipDepartmentRoads.isChecked = true
            "Sanitation" -> chipDepartmentSanitation.isChecked = true
            "Electricity" -> chipDepartmentElectricity.isChecked = true
            else -> chipDepartmentAll.isChecked = true
        }
    }

    private fun statusLabel(status: Int): String {
        return when (status) {
            1 -> "In Progress"
            2 -> "Resolved"
            else -> "Pending"
        }
    }

    private fun statusHue(status: Int): Float {
        return when (status) {
            1 -> BitmapDescriptorFactory.HUE_ORANGE
            2 -> BitmapDescriptorFactory.HUE_GREEN
            else -> BitmapDescriptorFactory.HUE_RED
        }
    }

    private fun departmentColor(department: String): Int {
        return when (department) {
            "Water" -> Color.parseColor("#2563EB")
            "Roads" -> Color.parseColor("#F97316")
            "Sanitation" -> Color.parseColor("#16A34A")
            "Electricity" -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#7C3AED")
        }
    }

    private fun departmentHue(department: String): Float {
        return when (department) {
            "Water" -> BitmapDescriptorFactory.HUE_AZURE
            "Roads" -> BitmapDescriptorFactory.HUE_ORANGE
            "Sanitation" -> BitmapDescriptorFactory.HUE_GREEN
            "Electricity" -> BitmapDescriptorFactory.HUE_YELLOW
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun normalizeDepartment(value: String): String {
        return when (value.trim().lowercase(Locale.getDefault())) {
            "water" -> "Water"
            "road", "roads" -> "Roads"
            "sanitation", "sanitisation" -> "Sanitation"
            "electricity", "electric" -> "Electricity"
            else -> value.trim()
        }
    }

    private fun isSupportedDepartment(value: String): Boolean {
        return normalizeDepartment(value) in setOf("Water", "Roads", "Sanitation", "Electricity")
    }

    private fun hasValidCoordinate(complaint: Complaint): Boolean {
        return complaint.latitude.absoluteValue > 0.0001 && complaint.longitude.absoluteValue > 0.0001
    }

    private fun List<Complaint>.validMapPoints(): List<LatLng> {
        return filter(::hasValidCoordinate).map { LatLng(it.latitude, it.longitude) }
    }

    private fun isNearBreach(complaint: Complaint): Boolean {
        if (complaint.status == 2 || complaint.timestamp <= 0L) return false
        val slaHours = slaHoursFor(complaint)
        val ageHours = (System.currentTimeMillis() - complaint.timestamp) / 3_600_000.0
        return ageHours in (slaHours * 0.8)..slaHours
    }

    private fun isOverdue(complaint: Complaint): Boolean {
        if (complaint.status == 2 || complaint.timestamp <= 0L) return false
        val ageHours = (System.currentTimeMillis() - complaint.timestamp) / 3_600_000.0
        return ageHours > slaHoursFor(complaint)
    }

    private fun slaHoursFor(complaint: Complaint): Double {
        return when (ComplaintDataFormatter.resolvedDepartment(complaint)) {
            "Sanitation" -> 24.0
            "Water" -> 48.0
            "Roads" -> 24.0 * 7
            "Electricity" -> 48.0
            else -> 72.0
        }
    }

    override fun onDestroyView() {
        complaintsListener?.let { listener ->
            complaintsRef?.removeEventListener(listener)
        }
        complaintsListener = null
        complaintsRef = null
        googleMap = null
        clusterManager = null
        super.onDestroyView()
    }

    private inner class ComplaintClusterRenderer(
        map: GoogleMap,
        clusterManager: ClusterManager<ComplaintClusterItem>
    ) : DefaultClusterRenderer<ComplaintClusterItem>(requireContext(), map, clusterManager) {

        override fun onBeforeClusterItemRendered(
            item: ComplaintClusterItem,
            markerOptions: MarkerOptions
        ) {
            markerOptions
                .title(item.getTitle())
                .snippet(
                    "${ComplaintDataFormatter.resolvedDepartment(item.complaint)} • ${statusLabel(item.complaint.status)}"
                )
                .icon(BitmapDescriptorFactory.defaultMarker(statusHue(item.complaint.status)))
        }

        override fun shouldRenderAsCluster(cluster: Cluster<ComplaintClusterItem>): Boolean {
            return cluster.size > 1
        }
    }

    companion object {
        private const val CLUSTER_CELL_SIZE = 0.02
        private const val HEATMAP_RADIUS = 40
        private const val RECENT_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        private val DEFAULT_MAP_CENTER = LatLng(22.5937, 78.9629)
    }
}
