package com.example.urban.bottomNavigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.urban.AppLocaleManager
import com.example.urban.R
import com.example.urban.bottomNavigation.alert.AlertsFragment
import com.example.urban.bottomNavigation.alert.AlertNotifier
import com.example.urban.bottomNavigation.alert.AlertItem
import com.example.urban.bottomNavigation.alert.AlertStorage
import com.example.urban.bottomNavigation.approvals.ApprovalsFragment
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintDetailFragment
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.bottomNavigation.complaint.ComplaintFragment
import com.example.urban.bottomNavigation.complaint.ComplaintSnapshotParser
import com.example.urban.bottomNavigation.drawer.DA.DepartmentAdminDetailFragment
import com.example.urban.bottomNavigation.drawer.DA.DepartmentAdminFragment
import com.example.urban.bottomNavigation.drawer.FO.FieldOfficerFragment
import com.example.urban.bottomNavigation.drawer.FO.OfficerDetailFragment
import com.example.urban.bottomNavigation.home.HomeFragment
import com.example.urban.bottomNavigation.map.MapFragment
import com.example.urban.bottomNavigation.profile.ProfileFragment
import com.example.urban.loginSingUp.AccountApprovalManager
import com.example.urban.loginSingUp.ApprovalRequest
import com.example.urban.loginSingUp.AppLockManager
import com.example.urban.loginSingUp.LoginActivity
import com.example.urban.loginSingUp.SessionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
        private const val MENU_MARK_ALL_READ = 1001
        private const val PREF_BADGE_STATE = "bottom_nav_badges"
        private const val KEY_ALERT_DOT = "alert_dot"
        private const val KEY_COMPLAINT_DOT = "complaint_dot"
        private const val ADMIN_NOTIFICATIONS_TOPIC = "admin_notifications"
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navDrawer: NavigationView
    private lateinit var toolbar: MaterialToolbar

    private var currentFragment: Fragment? = null
    private var isFreshLaunch = false
    private var openAlertsFromIntent = false
    private var pendingDestinationId: Int? = null
    private var isUnlockPromptShowing = false
    private var adminMessageListener: ChildEventListener? = null
    private var adminMessageRef: DatabaseReference? = null
    private var complaintListener: ChildEventListener? = null
    private var complaintRef: DatabaseReference? = null
    private var approvalsRef: DatabaseReference? = null
    private var approvalsListener: ValueEventListener? = null
    private val knownAdminMessageKeys = mutableSetOf<String>()
    private val knownComplaintKeys = mutableSetOf<String>()
    private val knownComplaintAssignments = mutableMapOf<String, String>()
    private val liveAlertComplaintKeys = mutableSetOf<String>()
    private var hasPendingApprovals = false

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private var currentUserRole = "Department Admin"
    private var currentUserDepartment = ""
    private var currentUserCityNormalized = ""
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val appLockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isUnlockPromptShowing = false
            if (result.resultCode == RESULT_OK) {
                SessionManager.markUnlocked(this)
            } else {
                moveTaskToBack(true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        isFreshLaunch = savedInstanceState == null
        openAlertsFromIntent = intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)

        bottomNav = findViewById(R.id.bottomNav)
        drawerLayout = findViewById(R.id.drawerLayout)
        navDrawer = findViewById(R.id.navDrawer)
        toolbar = findViewById(R.id.topBar)

        // Keep the colored icons as they are.
        bottomNav.itemIconTintList = null
        bottomNav.itemTextColor = AppCompatResources.getColorStateList(this, R.color.bottom_nav_icon_color)
        bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bottom_nav_active_indicator))
        refreshBottomNavBadges()

        setSupportActionBar(toolbar)
        applyPhoneNavigationBarStyle()
        AlertNotifier.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        SessionManager.refreshActivity(this)

        bottomNav.setOnItemSelectedListener {
            openDestination(it.itemId)
            true
        }

        toolbar.setNavigationOnClickListener {
            when (currentFragment) {
                is HomeFragment -> drawerLayout.openDrawer(GravityCompat.START)
                is DepartmentAdminFragment,
                is FieldOfficerFragment -> bottomNav.selectedItemId = R.id.nav_home
                is DepartmentAdminDetailFragment,
                is OfficerDetailFragment,
                is ComplaintDetailFragment -> handleToolbarBack()
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            syncToolbarWithCurrentFragment()
        }

        navDrawer.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_department_admins -> {
                    openDestination(R.id.nav_department_admins)
                }
                R.id.nav_officers -> {
                    openDestination(R.id.nav_officers)
                }
                R.id.nav_approvals -> {
                    openDestination(R.id.nav_approvals)
                }
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        loadDrawerUserData()
        fixOldUserData()
        fetchUserRole()
        saveDeviceToken()
    }

    // Opens the selected screen and updates drawer state.
    private fun loadFragment(fragment: Fragment) {
        if (!canNavigateNow()) return

        clearSecondaryBackStack()
        currentFragment = fragment

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        updateToolbarForFragment(fragment)
        invalidateOptionsMenu()
    }

    // This keeps the phone navigation bar in the light Azure shade across the app.
    private fun applyPhoneNavigationBarStyle() {
        window.navigationBarColor = ContextCompat.getColor(this, R.color.phone_navigation_bar_color)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    // This updates the toolbar and status bar colors for the active screen.
    private fun applyTopBarStyle(fragment: Fragment) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (fragment is ProfileFragment) {
            toolbar.visibility = View.GONE
            toolbar.setBackgroundResource(R.drawable.bg_profile_header)
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white))
            window.statusBarColor = ContextCompat.getColor(this, R.color.profile_header_start)
            controller.isAppearanceLightStatusBars = false
        } else {
            toolbar.visibility = View.VISIBLE
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.app_bar_color))
            toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.toolbar_text_dark))
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar_color)
            controller.isAppearanceLightStatusBars = true
        }
    }

    // This opens a screen by menu id and safely waits if the activity state is already saved.
    private fun openDestination(destinationId: Int) {
        if (!canNavigateNow()) {
            pendingDestinationId = destinationId
            return
        }

        pendingDestinationId = null

        when (destinationId) {
            R.id.nav_alerts -> clearAlertDot()
            R.id.nav_complaints -> clearComplaintDot()
        }

        when (destinationId) {
            R.id.nav_home -> loadFragment(HomeFragment())
            R.id.nav_complaints -> loadFragment(ComplaintFragment())
            R.id.nav_map -> loadFragment(MapFragment())
            R.id.nav_alerts -> loadFragment(AlertsFragment())
            R.id.nav_profile -> loadFragment(ProfileFragment())
            R.id.nav_department_admins -> loadFragment(DepartmentAdminFragment())
            R.id.nav_officers -> loadFragment(FieldOfficerFragment())
            R.id.nav_approvals -> loadFragment(ApprovalsFragment())
        }
    }

    // This updates the toolbar after back stack changes from child screens.
    private fun syncToolbarWithCurrentFragment() {
        val visibleFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) ?: return
        currentFragment = visibleFragment
        updateToolbarForFragment(visibleFragment)
        invalidateOptionsMenu()
    }

    // This sets the correct title and left icon for root and sub pages.
    private fun updateToolbarForFragment(fragment: Fragment) {
        when (fragment) {
            is HomeFragment -> {
                updateDrawerNavigationIcon()
                toolbar.title = getString(R.string.toolbar_dashboard)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
            is DepartmentAdminFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.toolbar_department_admins)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is DepartmentAdminDetailFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.toolbar_department_admins)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is FieldOfficerFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.toolbar_field_officers)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is OfficerDetailFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.toolbar_officer_detail)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is ComplaintDetailFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.toolbar_complaint_detail)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is ApprovalsFragment -> {
                toolbar.setNavigationIcon(R.drawable.ic_toolbar_back)
                toolbar.title = getString(R.string.approval_title)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is ProfileFragment -> {
                toolbar.navigationIcon = null
                toolbar.title = ""
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is ComplaintFragment -> {
                toolbar.navigationIcon = null
                toolbar.title = getString(R.string.toolbar_complaints)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is AlertsFragment -> {
                toolbar.navigationIcon = null
                toolbar.title = getString(R.string.toolbar_notifications)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            is MapFragment -> {
                toolbar.navigationIcon = null
                toolbar.title = getString(R.string.toolbar_map)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            else -> {
                toolbar.navigationIcon = null
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }

        applyTopBarStyle(fragment)
    }

    // This handles the shared toolbar back action for deeper pages.
    private fun handleToolbarBack() {
        if (supportFragmentManager.backStackEntryCount > 0 && !supportFragmentManager.isStateSaved) {
            supportFragmentManager.popBackStack()
            return
        }

        when (currentFragment) {
            is DepartmentAdminFragment,
            is DepartmentAdminDetailFragment,
            is FieldOfficerFragment,
            is OfficerDetailFragment,
            is ApprovalsFragment -> bottomNav.selectedItemId = R.id.nav_home
            is ComplaintDetailFragment -> bottomNav.selectedItemId = R.id.nav_complaints
        }
    }

    // This clears old sub-page history when the user opens a root screen.
    private fun clearSecondaryBackStack() {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    // This decides whether fragment navigation is safe right now.
    private fun canNavigateNow(): Boolean {
        return !isFinishing &&
            !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !supportFragmentManager.isStateSaved
    }

    // This runs any delayed navigation once the activity is active again.
    private fun processPendingNavigation() {
        val destinationId = pendingDestinationId ?: return
        if (!canNavigateNow()) return

        pendingDestinationId = null
        if (isBottomNavDestination(destinationId)) {
            if (bottomNav.selectedItemId != destinationId) {
                bottomNav.selectedItemId = destinationId
            } else {
                openDestination(destinationId)
            }
        } else {
            openDestination(destinationId)
        }
    }

    // This checks whether a menu id belongs to the bottom navigation bar.
    private fun isBottomNavDestination(destinationId: Int): Boolean {
        return destinationId == R.id.nav_home ||
            destinationId == R.id.nav_complaints ||
            destinationId == R.id.nav_map ||
            destinationId == R.id.nav_alerts ||
            destinationId == R.id.nav_profile
    }

    override fun onPostResume() {
        super.onPostResume()
        processPendingNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menu.clear()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()

        if (currentFragment is AlertsFragment) {
            addAlertToolbarButton(
                menu = menu,
                itemId = MENU_MARK_ALL_READ,
                text = getString(R.string.alerts_mark_read_short)
            ) {
                (currentFragment as? AlertsFragment)?.markAllReadFromToolbar()
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    // Adds one rounded toolbar button for the alerts screen actions.
    private fun addAlertToolbarButton(
        menu: Menu,
        itemId: Int,
        text: String,
        onClick: () -> Unit
    ) {
        val item = menu.add(Menu.NONE, itemId, Menu.NONE, text)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        val button = LayoutInflater.from(this)
            .inflate(R.layout.toolbar_alert_action_button, toolbar, false) as MaterialButton
        button.text = text
        button.setOnClickListener { onClick() }
        item.actionView = button
    }

    // Loads the current user role and department.
    private fun fetchUserRole() {

        val uid = auth.currentUser?.uid ?: run {
            redirectToLogin(sessionExpired = false)
            return
        }

        database.child("Users")
            .child(uid)
            .get()
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (!it.exists()) {
                    redirectToLogin(sessionExpired = false)
                    return@addOnSuccessListener
                }

                val role = resolveRole(it.child("role").value?.toString())
                val department = it.child("department").value?.toString().orEmpty()
                val accountStatus = AccountApprovalManager.effectiveStatus(
                    it.child("accountStatus").value?.toString()
                )

                if (accountStatus != AccountApprovalManager.STATUS_APPROVED) {
                    redirectToLogin(sessionExpired = false)
                    return@addOnSuccessListener
                }

                currentUserRole = role
                currentUserDepartment = ComplaintDataFormatter.normalizeDepartment(department)
                    ?: department.trim()
                currentUserCityNormalized = ComplaintDataFormatter.normalizeCity(
                    it.child("cityNormalized").value?.toString().orEmpty()
                        .ifBlank { it.child("city").value?.toString().orEmpty() }
                )
                syncAdminNotificationTopic(role)
                setupBottomNav(role)
            }
            .addOnFailureListener {
                redirectToLogin(sessionExpired = false)
            }
    }

    // Applies role-based navigation rules.
    private fun setupBottomNav(role: String) {

        val menu = bottomNav.menu
        navDrawer.menu.findItem(R.id.nav_officers).isVisible = role != "Field Officer"
        navDrawer.menu.findItem(R.id.nav_department_admins).isVisible = role == AccountApprovalManager.ROLE_SUPER_ADMIN
        navDrawer.menu.findItem(R.id.nav_approvals).isVisible = role == AccountApprovalManager.ROLE_SUPER_ADMIN

        if (role == "Field Officer") {
            menu.findItem(R.id.nav_home).isVisible = false
        }

        if (isFreshLaunch) {
            loadDefaultFragmentForRole(role)
            isFreshLaunch = false
        }

        if (role == "Field Officer") {
            stopAdminMessageListener()
            startComplaintListener()
            stopApprovalsListener()
        } else if (role == AccountApprovalManager.ROLE_SUPER_ADMIN) {
            startAdminMessageListener()
            startComplaintListener()
            startApprovalsListener()
        } else {
            startAdminMessageListener()
            startComplaintListener()
            stopApprovalsListener()
        }
    }

    // Picks the first screen for the current role.
    private fun loadDefaultFragmentForRole(role: String) {
        val defaultItemId = if (openAlertsFromIntent) {
            R.id.nav_alerts
        } else if (role == "Field Officer") {
            R.id.nav_complaints
        } else {
            R.id.nav_home
        }

        if (canNavigateNow()) {
            if (bottomNav.selectedItemId != defaultItemId) {
                bottomNav.selectedItemId = defaultItemId
            } else {
                openDestination(defaultItemId)
            }
        } else {
            pendingDestinationId = defaultItemId
        }

        openAlertsFromIntent = false
    }

    // Saves the latest FCM token in Firebase.
    private fun saveDeviceToken() {

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->

                if (!task.isSuccessful) return@addOnCompleteListener

                val token = task.result
                val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                database.child("Users")
                    .child(uid)
                    .child("deviceToken")
                    .setValue(token)
            }
    }

    // Keeps the complaint-report FCM topic aligned with the current approved role.
    private fun syncAdminNotificationTopic(role: String) {
        val shouldReceiveComplaintReports = role == AccountApprovalManager.ROLE_SUPER_ADMIN ||
            role == AccountApprovalManager.ROLE_DEPARTMENT_ADMIN

        val topicTask = if (shouldReceiveComplaintReports) {
            FirebaseMessaging.getInstance().subscribeToTopic(ADMIN_NOTIFICATIONS_TOPIC)
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(ADMIN_NOTIFICATIONS_TOPIC)
        }

        topicTask.addOnFailureListener {
            // We keep the app usable even if topic sync fails once.
        }
    }

    // Requests notification permission on Android 13+.
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Starts the live AdminMessages listener.
    private fun startAdminMessageListener() {
        if (adminMessageRef != null) return

        val ref = FirebaseDatabase.getInstance().getReference("AdminMessages")
        adminMessageRef = ref

        // Warm up known keys so only new events trigger alerts.
        ref.get()
            .addOnSuccessListener { snapshot ->
                knownAdminMessageKeys.clear()
                snapshot.children.mapNotNullTo(knownAdminMessageKeys) { it.key }
                attachAdminMessageListener(ref)
            }
            .addOnFailureListener {
                knownAdminMessageKeys.clear()
                attachAdminMessageListener(ref)
            }
    }

    // Hooks the AdminMessages child listener.
    private fun attachAdminMessageListener(ref: DatabaseReference) {
        if (adminMessageListener != null) return

        adminMessageListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageKey = snapshot.key.orEmpty()
                if (messageKey.isNotBlank() && !knownAdminMessageKeys.add(messageKey)) return

                val alert = buildAdminMessageAlert(snapshot)
                val eventKey = "new_report:${alert.complaintKey.ifBlank { messageKey }}"
                if (!shouldDispatchLiveAlert(eventKey)) return

                dispatchAlert(alert)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) = Unit
        }

        ref.addChildEventListener(adminMessageListener as ChildEventListener)
    }

    // Builds an alert item from AdminMessages data.
    private fun buildAdminMessageAlert(snapshot: DataSnapshot): AlertItem {
        val title = snapshot.readAdminMessageValue("title")
            .ifBlank { snapshot.readAdminMessageValue("tle") }
            .ifBlank { snapshot.readAdminMessageValue(" tle") }
            .ifBlank { "New Report" }
        val body = snapshot.readAdminMessageValue("body")
            .ifBlank { "A new complaint report has been received." }
        val linkedComplaintId = snapshot.readAdminMessageValue("complaintId")
        val eventTimestamp = snapshot.child("timestamp").getValue(Long::class.java)
            ?: System.currentTimeMillis()

        return AlertItem(
            id = snapshot.key ?: UUID.randomUUID().toString(),
            title = title,
            body = body,
            type = "New Report",
            timestamp = eventTimestamp,
            complaintKey = linkedComplaintId,
            complaintDisplayId = linkedComplaintId
        )
    }

    // Also watch Complaints directly as a fallback.
    private fun startComplaintListener() {
        if (complaintRef != null) return

        val ref = FirebaseDatabase.getInstance().getReference("Complaints")
        complaintRef = ref

        ref.get()
            .addOnSuccessListener { snapshot ->
                knownComplaintKeys.clear()
                knownComplaintAssignments.clear()

                ComplaintSnapshotParser.complaintSnapshots(snapshot).forEach { complaintSnapshot ->
                    val complaintPath = complaintSnapshot.ref.path.toString()
                    if (complaintPath.isBlank()) return@forEach

                    knownComplaintKeys.add(complaintPath)

                    val complaint = ComplaintSnapshotParser.fromSnapshot(complaintSnapshot) ?: return@forEach
                    knownComplaintAssignments[complaintPath] = complaint.allottedOfficerId.trim()
                }
                attachComplaintListener(ref)
            }
            .addOnFailureListener {
                knownComplaintKeys.clear()
                knownComplaintAssignments.clear()
                attachComplaintListener(ref)
            }
    }

    private fun attachComplaintListener(ref: DatabaseReference) {
        if (complaintListener != null) return

        complaintListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                ComplaintSnapshotParser.complaintSnapshots(snapshot).forEach { complaintSnapshot ->
                    val complaintPath = complaintSnapshot.ref.path.toString()
                    if (complaintPath.isNotBlank() && !knownComplaintKeys.add(complaintPath)) return@forEach

                    val complaint = ComplaintSnapshotParser.fromSnapshot(complaintSnapshot) ?: return@forEach
                    knownComplaintAssignments[complaintPath] = complaint.allottedOfficerId.trim()

                    when (currentUserRole) {
                        "Super Admin", "Department Admin" -> {
                            if (!shouldNotifyForComplaint(complaint)) return@forEach

                            val alert = buildComplaintAlert(complaintSnapshot, complaint)
                            val eventKey = "new_report:${alert.complaintKey.ifBlank { complaintPath }}"
                            if (!shouldDispatchLiveAlert(eventKey)) return@forEach

                            dispatchAlert(alert)
                        }

                        "Field Officer" -> {
                            val currentUserId = auth.currentUser?.uid.orEmpty()
                            if (currentUserId.isBlank()) return@forEach
                            if (!ComplaintDataFormatter.matchesCity(complaint, currentUserCityNormalized)) return@forEach
                            if (!complaint.allottedOfficerId.trim().equals(currentUserId, ignoreCase = false)) return@forEach

                            val alert = buildAssignmentAlert(complaintSnapshot, complaint)
                            val eventKey = "assignment:${alert.complaintKey.ifBlank { complaintPath }}"
                            if (!shouldDispatchLiveAlert(eventKey)) return@forEach

                            dispatchAlert(alert)
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (currentUserRole != "Field Officer") return

                ComplaintSnapshotParser.complaintSnapshots(snapshot).forEach { complaintSnapshot ->
                    val complaintPath = complaintSnapshot.ref.path.toString()
                    if (complaintPath.isBlank()) return@forEach

                    val complaint = ComplaintSnapshotParser.fromSnapshot(complaintSnapshot) ?: return@forEach

                    val previousOfficerId = knownComplaintAssignments[complaintPath].orEmpty()
                    val currentOfficerId = complaint.allottedOfficerId.trim()
                    knownComplaintAssignments[complaintPath] = currentOfficerId

                    val currentUserId = auth.currentUser?.uid.orEmpty()
                    if (currentUserId.isBlank()) return@forEach
                    if (!ComplaintDataFormatter.matchesCity(complaint, currentUserCityNormalized)) return@forEach

                    val wasAssignedToCurrentUser = previousOfficerId == currentUserId
                    val isAssignedToCurrentUser = currentOfficerId == currentUserId

                    if (wasAssignedToCurrentUser || !isAssignedToCurrentUser) return@forEach

                    val alert = buildAssignmentAlert(complaintSnapshot, complaint)
                    val eventKey = "assignment:${alert.complaintKey.ifBlank { complaintPath }}"
                    if (!shouldDispatchLiveAlert(eventKey)) return@forEach

                    dispatchAlert(alert)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) = Unit
        }

        ref.addChildEventListener(complaintListener as ChildEventListener)
    }

    private fun buildComplaintAlert(snapshot: DataSnapshot, complaint: Complaint): AlertItem {
        val complaintKey = snapshot.key.orEmpty()
        val complaintDisplayId = complaint.complaintId.ifBlank { complaintKey }
        val department = ComplaintDataFormatter.resolvedDepartment(complaint)
        val location = complaint.location.trim().ifBlank { "selected area" }
        val issueType = complaint.issueType.trim().ifBlank { "General issue" }
        val eventTimestamp = complaint.timestamp
            .takeIf { it > 0L }
            ?: snapshot.child("timestamp").getValue(Long::class.java)
            ?: snapshot.child("timeStamp").getValue(Long::class.java)
            ?: System.currentTimeMillis()

        return AlertItem(
            id = complaintKey.ifBlank { snapshot.key ?: UUID.randomUUID().toString() },
            title = "New Complaint Reported",
            body = "$issueType reported from $location • $department",
            type = "New Report",
            timestamp = eventTimestamp,
            complaintKey = complaintKey,
            complaintDisplayId = complaintDisplayId
        )
    }

    private fun buildAssignmentAlert(snapshot: DataSnapshot, complaint: Complaint): AlertItem {
        val complaintKey = snapshot.key.orEmpty()
        val complaintDisplayId = complaint.complaintId.ifBlank { complaintKey }
        val issueType = complaint.issueType.trim().ifBlank { "General issue" }
        val location = complaint.location.trim().ifBlank { "selected area" }
        val eventTimestamp = complaint.updatedAt
            .takeIf { it > 0L }
            ?: complaint.timestamp.takeIf { it > 0L }
            ?: snapshot.child("updatedAt").getValue(Long::class.java)
            ?: snapshot.child("timestamp").getValue(Long::class.java)
            ?: System.currentTimeMillis()

        return AlertItem(
            id = "assignment_$complaintKey",
            title = "Complaint Assigned",
            body = "$issueType at $location is now assigned to you.",
            type = "Assignment",
            timestamp = eventTimestamp,
            complaintKey = complaintKey,
            complaintDisplayId = complaintDisplayId
        )
    }

    private fun shouldNotifyForComplaint(complaint: Complaint): Boolean {
        return ComplaintDataFormatter.isVisibleToUser(
            complaint = complaint,
            role = currentUserRole,
            userDepartment = currentUserDepartment,
            userCityNormalized = currentUserCityNormalized,
            userUid = auth.currentUser?.uid.orEmpty()
        )
    }

    private fun shouldDispatchLiveAlert(eventKey: String): Boolean {
        if (eventKey.isBlank()) return true
        return liveAlertComplaintKeys.add(eventKey)
    }

    private fun dispatchAlert(alert: AlertItem) {
        AlertStorage.addAlert(this, alert)
        setAlertDotVisible(currentFragment !is AlertsFragment)
        if (alert.complaintKey.isNotBlank()) {
            val complaintScreenOpen = currentFragment is ComplaintFragment || currentFragment is ComplaintDetailFragment
            setComplaintDotVisible(!complaintScreenOpen)
        }
        AlertNotifier.show(this, alert)
        Toast.makeText(
            this,
            "NEW REPORT: ${alert.title}\nID: ${alert.complaintDisplayId.ifBlank { "N/A" }}",
            Toast.LENGTH_LONG
        ).show()
    }

    // This listens for pending approval requests assigned to the current super admin.
    private fun startApprovalsListener() {
        if (approvalsRef != null) return

        val uid = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("ApprovalRequests")
        approvalsRef = ref

        approvalsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pendingCount = snapshot.children.mapNotNull { child ->
                    child.getValue(ApprovalRequest::class.java)
                }.count {
                    it.status == AccountApprovalManager.STATUS_PENDING &&
                        it.approvalRoute == AccountApprovalManager.ROUTE_CITY_SUPER_ADMIN &&
                        it.targetApproverUid == uid
                }

                hasPendingApprovals = pendingCount > 0
                updateDrawerApprovalBadge(hasPendingApprovals)
                if (currentFragment is HomeFragment) {
                    updateDrawerNavigationIcon()
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        ref.addValueEventListener(approvalsListener as ValueEventListener)
    }

    // This stops the pending approval listener when the role does not need it.
    private fun stopApprovalsListener() {
        val ref = approvalsRef
        val listener = approvalsListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }

        approvalsListener = null
        approvalsRef = null
        hasPendingApprovals = false
        updateDrawerApprovalBadge(false)
        if (currentFragment is HomeFragment) {
            updateDrawerNavigationIcon()
        }
    }

    // This switches the drawer icon between plain and dotted versions.
    private fun updateDrawerNavigationIcon() {
        toolbar.setNavigationIcon(
            if (hasPendingApprovals) {
                R.drawable.ic_drawer_with_dot
            } else {
                R.drawable.navi_drawer
            }
        )
    }

    // This shows a small red dot beside the drawer approval item.
    private fun updateDrawerApprovalBadge(visible: Boolean) {
        val menuItem = navDrawer.menu.findItem(R.id.nav_approvals) ?: return

        if (visible) {
            if (menuItem.actionView == null) {
                menuItem.setActionView(R.layout.view_drawer_badge_dot)
            } else {
                menuItem.actionView?.visibility = View.VISIBLE
            }
        } else {
            menuItem.actionView = null
        }
    }

    // Stores and draws the small red dots on bottom navigation icons.
    private fun refreshBottomNavBadges() {
        applyDotBadge(
            itemId = R.id.nav_alerts,
            visible = badgePrefs().getBoolean(KEY_ALERT_DOT, false)
        )
        applyDotBadge(
            itemId = R.id.nav_complaints,
            visible = badgePrefs().getBoolean(KEY_COMPLAINT_DOT, false)
        )
    }

    // Shows or hides one dot badge on a bottom navigation item.
    private fun applyDotBadge(itemId: Int, visible: Boolean) {
        if (visible) {
            val badge = bottomNav.getOrCreateBadge(itemId)
            badge.backgroundColor = ContextCompat.getColor(this, android.R.color.holo_red_dark)
            badge.badgeGravity = BadgeDrawable.TOP_END
            badge.clearNumber()
            badge.isVisible = true
            badge.horizontalOffset = 6
            badge.verticalOffset = 8
        } else {
            bottomNav.removeBadge(itemId)
        }
    }

    // Saves the alert dot state.
    private fun setAlertDotVisible(visible: Boolean) {
        badgePrefs().edit().putBoolean(KEY_ALERT_DOT, visible).apply()
        applyDotBadge(R.id.nav_alerts, visible)
    }

    // Saves the complaint dot state.
    private fun setComplaintDotVisible(visible: Boolean) {
        badgePrefs().edit().putBoolean(KEY_COMPLAINT_DOT, visible).apply()
        applyDotBadge(R.id.nav_complaints, visible)
    }

    // Clears the alerts tab dot once the screen is opened.
    private fun clearAlertDot() {
        setAlertDotVisible(false)
    }

    // Clears the complaints tab dot once the list is opened.
    private fun clearComplaintDot() {
        setComplaintDotVisible(false)
    }

    // Shared preferences used for small tab-dot states.
    private fun badgePrefs() = getSharedPreferences(PREF_BADGE_STATE, MODE_PRIVATE)

    private fun stopAdminMessageListener() {
        val ref = adminMessageRef
        val listener = adminMessageListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }

        adminMessageListener = null
        adminMessageRef = null
        knownAdminMessageKeys.clear()
    }

    private fun stopComplaintListener() {
        val ref = complaintRef
        val listener = complaintListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }

        complaintListener = null
        complaintRef = null
        knownComplaintKeys.clear()
        knownComplaintAssignments.clear()
    }

    private fun DataSnapshot.readAdminMessageValue(childKey: String): String =
        child(childKey).value?.toString()?.trim().orEmpty()

    private fun resolveRole(rawRole: String?): String {
        return when (rawRole?.trim()) {
            "Super Admin" -> "Super Admin"
            "Department Admin" -> "Department Admin"
            "Field Officer" -> "Field Officer"
            else -> "Department Admin"
        }
    }

    private fun redirectToLogin(sessionExpired: Boolean) {
        stopAdminMessageListener()
        stopComplaintListener()
        stopApprovalsListener()
        FirebaseMessaging.getInstance().unsubscribeFromTopic(ADMIN_NOTIFICATIONS_TOPIC)
        auth.signOut()
        SessionManager.clear(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            putExtra(SessionManager.EXTRA_SESSION_EXPIRED, sessionExpired)
            if (sessionExpired) {
                putExtra(SessionManager.EXTRA_SESSION_MESSAGE, "Session expired after staying away from the app for too long.")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ================= DRAWER USER =================

    private fun loadDrawerUserData() {

        val header = navDrawer.getHeaderView(0)

        val imgProfile = header.findViewById<ImageView>(R.id.imgProfile)
        val tvName = header.findViewById<TextView>(R.id.tvName)
        val tvRole = header.findViewById<TextView>(R.id.tvRole)

        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener {

                val name = it.child("name").value?.toString() ?: "User"
                val role = it.child("role").value?.toString() ?: ""
                val image = it.child("profileImageUrl").value?.toString()

                tvName.text = name
                tvRole.text = role

                if (!image.isNullOrEmpty() && image.startsWith("http")) {

                    Glide.with(this)
                        .load(image)
                        .placeholder(R.drawable.users)
                        .error(R.drawable.users)
                        .into(imgProfile)

                } else {
                    imgProfile.setImageResource(R.drawable.users)
                }
            }
    }

    // ================= FIX OLD USERS =================

    private fun fixOldUserData() {

        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener {

                val updates = HashMap<String, Any>()

                val role = it.child("role").value
                val dept = it.child("department").value
                val img = it.child("profileImageUrl").value

                if (role == null || role.toString().isEmpty()) {
                    updates["role"] = "Department Admin"
                }

                if (dept == null || dept.toString().isEmpty()) {
                    updates["department"] = "General"
                }

                if (img == null) {
                    updates["profileImageUrl"] = ""
                }

                if (updates.isNotEmpty()) {
                    database.child("Users").child(uid).updateChildren(updates)
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            redirectToLogin(sessionExpired = false)
            return
        }

        SessionManager.refreshActivity(this)
    }

    // Shows phone security when the app is reopened after going to background.
    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) return
        promptForAppUnlockIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && !isUnlockPromptShowing) {
            SessionManager.markBackgrounded(this)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        SessionManager.refreshActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdminMessageListener()
        stopComplaintListener()
        stopApprovalsListener()
    }

    // Opens biometric or phone lock when the user comes back to the app.
    private fun promptForAppUnlockIfNeeded() {
        if (!SessionManager.isAppLockRequired(this)) return
        if (isUnlockPromptShowing) return

        val unlockIntent = AppLockManager.createUnlockIntent(this)
        if (unlockIntent == null) {
            SessionManager.markUnlocked(this)
            return
        }

        isUnlockPromptShowing = true
        appLockLauncher.launch(unlockIntent)
    }

}
//package com.example.urban.bottomNavigation
//
//import android.os.Bundle
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.drawerlayout.widget.DrawerLayout
//import androidx.fragment.app.Fragment
//import androidx.core.view.GravityCompat
//import com.bumptech.glide.Glide
//import com.example.urban.R
//import com.example.urban.bottomNavigation.alert.AlertsFragment
//import com.example.urban.bottomNavigation.complaint.ComplaintFragment
//import com.example.urban.bottomNavigation.home.HomeFragment
//import com.example.urban.bottomNavigation.map.MapFragment
//import com.example.urban.bottomNavigation.profile.ProfileFragment
//import com.google.android.material.appbar.MaterialToolbar
//import com.google.android.material.bottomnavigation.BottomNavigationView
//import com.google.android.material.navigation.NavigationView
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.FirebaseDatabase
//import com.google.firebase.messaging.FirebaseMessaging
//
//class DashboardActivity : AppCompatActivity() {
//
//    private lateinit var bottomNav: BottomNavigationView
//    private lateinit var drawerLayout: DrawerLayout
//    private lateinit var navDrawer: NavigationView
//    private lateinit var toolbar: MaterialToolbar
//
//    private val auth = FirebaseAuth.getInstance()
//    private val database = FirebaseDatabase.getInstance().reference
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_dashboard)
//
//        bottomNav = findViewById(R.id.bottomNav)
//        drawerLayout = findViewById(R.id.drawerLayout)
//        navDrawer = findViewById(R.id.navDrawer)
//        toolbar = findViewById(R.id.topBar)
//
//        // Load first screen
//        loadFragment(HomeFragment())
//
//        // Bottom Navigation
//        bottomNav.setOnItemSelectedListener {
//            when (it.itemId) {
//                R.id.nav_home -> loadFragment(HomeFragment())
//                R.id.nav_complaints -> loadFragment(ComplaintFragment())
//                R.id.nav_map -> loadFragment(MapFragment())
//                R.id.nav_alerts -> loadFragment(AlertsFragment())
//                R.id.nav_profile -> loadFragment(ProfileFragment())
//            }
//            true
//        }
//
//        // Drawer User Data
//        loadDrawerUserData()
//
//        // Fix old users
//        fixOldUserData()
//
//        // Toolbar click (ONLY when enabled)
//        toolbar.setNavigationOnClickListener {
//            if (drawerLayout.getDrawerLockMode(GravityCompat.START)
//                == DrawerLayout.LOCK_MODE_UNLOCKED) {
//
//                drawerLayout.openDrawer(GravityCompat.START)
//            }
//        }
//
//        // Drawer menu click
//        navDrawer.setNavigationItemSelectedListener {
//
//            when (it.itemId) {
//                R.id.nav_officers -> {
//                    // TODO: Open Field Officer Screen
//                }
//            }
//
//            drawerLayout.closeDrawer(GravityCompat.START)
//            true
//        }
//
//        fetchUserRole()
//        saveDeviceToken()
//    }
//
//    // ================= LOAD FRAGMENT + DRAWER CONTROL =================
//
//    private fun loadFragment(fragment: Fragment) {
//
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragmentContainer, fragment)
//            .commit()
//
//        if (fragment is HomeFragment) {
//
//            // Show drawer
//            toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
//            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
//
//        } else {
//
//            // Hide drawer
//            toolbar.navigationIcon = null
//            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
//        }
//    }
//
//    // ================= USER ROLE =================
//
//    private fun fetchUserRole() {
//
//        val uid = auth.currentUser?.uid ?: return
//
//        database.child("Users")
//            .child(uid)
//            .get()
//            .addOnSuccessListener {
//
//                val role = it.child("role").value.toString()
//                setupBottomNav(role)
//            }
//    }
//
//    private fun setupBottomNav(role: String) {
//
//        val menu = bottomNav.menu
//
//        if (role == "Field Officer") {
//            menu.findItem(R.id.nav_home).isVisible = false
//        }
//    }
//
//    // ================= FCM TOKEN =================
//
//    private fun saveDeviceToken() {
//
//        FirebaseMessaging.getInstance().token
//            .addOnCompleteListener { task ->
//
//                if (!task.isSuccessful) return@addOnCompleteListener
//
//                val token = task.result
//                val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
//
//                database.child("Users")
//                    .child(uid)
//                    .child("deviceToken")
//                    .setValue(token)
//            }
//    }
//
//    // ================= DRAWER USER DATA =================
//
//    private fun loadDrawerUserData() {
//
//        val header = navDrawer.getHeaderView(0)
//
//        val imgProfile = header.findViewById<ImageView>(R.id.imgProfile)
//        val tvName = header.findViewById<TextView>(R.id.tvName)
//        val tvRole = header.findViewById<TextView>(R.id.tvRole)
//
//        val uid = auth.currentUser?.uid ?: return
//
//        database.child("Users").child(uid)
//            .get()
//            .addOnSuccessListener {
//
//                val name = it.child("name").value?.toString() ?: "User"
//                val role = it.child("role").value?.toString() ?: ""
//                val image = it.child("profileImageUrl").value?.toString()
//
//                tvName.text = name
//                tvRole.text = role
//
//                if (!image.isNullOrEmpty() && image.startsWith("http")) {
//
//                    Glide.with(this)
//                        .load(image)
//                        .placeholder(R.drawable.users)
//                        .error(R.drawable.users)
//                        .into(imgProfile)
//
//                } else {
//                    imgProfile.setImageResource(R.drawable.users)
//                }
//            }
//    }
//
//    // ================= FIX OLD USERS =================
//
//    private fun fixOldUserData() {
//
//        val uid = auth.currentUser?.uid ?: return
//
//        database.child("Users").child(uid)
//            .get()
//            .addOnSuccessListener {
//
//                val updates = HashMap<String, Any>()
//
//                val role = it.child("role").value
//                val dept = it.child("department").value
//                val img = it.child("profileImageUrl").value
//
//                if (role == null || role.toString().isEmpty()) {
//                    updates["role"] = "Department Admin"
//                }
//
//                if (dept == null || dept.toString().isEmpty()) {
//                    updates["department"] = "General"
//                }
//
//                if (img == null) {
//                    updates["profileImageUrl"] = ""
//                }
//
//                if (updates.isNotEmpty()) {
//                    database.child("Users").child(uid).updateChildren(updates)
//                }
//            }
//    }
//}
