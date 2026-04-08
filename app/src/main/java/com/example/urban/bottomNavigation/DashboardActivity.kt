package com.example.urban.bottomNavigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.bottomNavigation.alert.AlertsFragment
import com.example.urban.bottomNavigation.alert.AlertNotifier
import com.example.urban.bottomNavigation.alert.AlertItem
import com.example.urban.bottomNavigation.alert.AlertStorage
import com.example.urban.bottomNavigation.complaint.ComplaintFragment
import com.example.urban.bottomNavigation.drawer.FO.FieldOfficerFragment
import com.example.urban.bottomNavigation.home.HomeFragment
import com.example.urban.bottomNavigation.map.MapFragment
import com.example.urban.bottomNavigation.profile.ProfileFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navDrawer: NavigationView
    private lateinit var toolbar: MaterialToolbar

    private var currentFragment: Fragment? = null
    private var isFreshLaunch = false
    private var openAlertsFromIntent = false
    private var adminMessageListener: ChildEventListener? = null
    private var adminMessageRef: DatabaseReference? = null
    private val knownAdminMessageKeys = mutableSetOf<String>()

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        isFreshLaunch = savedInstanceState == null
        openAlertsFromIntent = intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)

        // ================= INIT =================
        bottomNav = findViewById(R.id.bottomNav)
        drawerLayout = findViewById(R.id.drawerLayout)
        navDrawer = findViewById(R.id.navDrawer)
        toolbar = findViewById(R.id.topBar)

        // Keep the original multicolor menu drawables visible instead of tinting them to one flat color.
        bottomNav.itemIconTintList = null
        bottomNav.itemTextColor = AppCompatResources.getColorStateList(this, R.color.bottom_nav_icon_color)

        setSupportActionBar(toolbar)
        AlertNotifier.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        // ================= BOTTOM NAVIGATION =================
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_complaints -> loadFragment(ComplaintFragment())
                R.id.nav_map -> loadFragment(MapFragment())
                R.id.nav_alerts -> loadFragment(AlertsFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
            }
            true
        }

        // ================= TOOLBAR CLICK =================
        toolbar.setNavigationOnClickListener {

            if (currentFragment is HomeFragment) {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
//        toolbar.setNavigationOnClickListener {
//
//            if (drawerLayout.getDrawerLockMode(GravityCompat.START)
//                == DrawerLayout.LOCK_MODE_UNLOCKED) {
//
//                drawerLayout.openDrawer(GravityCompat.START)
//            }
//        }

        // ================= DRAWER MENU =================
        navDrawer.setNavigationItemSelectedListener {

            when (it.itemId) {

                R.id.nav_officers -> {
                    loadFragment(FieldOfficerFragment())
                }
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            true}

        // ================= LOAD USER DATA =================
        loadDrawerUserData()

        // ================= FIX OLD USERS =================
        fixOldUserData()

        // ================= ROLE BASED UI =================
        fetchUserRole()

        // ================= FCM TOKEN =================
        saveDeviceToken()


    }

    // ================= FRAGMENT + DRAWER CONTROL =================

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        //  Drawer logic
        if (fragment is HomeFragment) {

            toolbar.setNavigationIcon(R.drawable.navi_drawer)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        } else {

            toolbar.navigationIcon = null
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

        //  Title change
        when (fragment) {
            is HomeFragment -> toolbar.title = "Dashboard"
            is ProfileFragment -> toolbar.title = "Your Profile"
            is ComplaintFragment -> toolbar.title = "Complaints"
            is AlertsFragment -> toolbar.title = "Notifications"
            is MapFragment -> toolbar.title = "Map"
            is FieldOfficerFragment -> toolbar.title = "Field Officers"
        }

        //  Refresh menu
        invalidateOptionsMenu()
    }




//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragmentContainer, fragment)
//            .commit()
//
//        if (fragment is HomeFragment) {
//
//            // ✅ SHOW DRAWER ONLY HERE
//            toolbar.setNavigationIcon(R.drawable.navi_drawer)
//            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
//
//        } else {
//
//            // ❌ HIDE DRAWER
//            toolbar.navigationIcon = null
//            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
//        }
//    }
//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//
//    if (currentFragment is ProfileFragment) {
//        menuInflater.inflate(R.menu.profile_menu, menu)
//        return true
//    }
//
//    return super.onCreateOptionsMenu(menu)
//}
override fun onCreateOptionsMenu(menu: Menu): Boolean {

    menu.clear()
    return true
}
    // ================= ROLE =================

    private fun fetchUserRole() {

        val uid = auth.currentUser?.uid ?: return

        database.child("Users")
            .child(uid)
            .get()
            .addOnSuccessListener {

                val role = it.child("role").value.toString()
                setupBottomNav(role)
            }
    }

    private fun setupBottomNav(role: String) {

        val menu = bottomNav.menu
        navDrawer.menu.findItem(R.id.nav_officers).isVisible = role != "Field Officer"

        if (role == "Field Officer") {
            menu.findItem(R.id.nav_home).isVisible = false
        }

        if (isFreshLaunch) {
            loadDefaultFragmentForRole(role)
            isFreshLaunch = false
        }

        if (role == "Field Officer") {
            stopAdminMessageListener()
        } else {
            startAdminMessageListener()
        }
    }

    private fun loadDefaultFragmentForRole(role: String) {
        val defaultItemId = if (openAlertsFromIntent) {
            R.id.nav_alerts
        } else if (role == "Field Officer") {
            R.id.nav_complaints
        } else {
            R.id.nav_home
        }

        bottomNav.selectedItemId = defaultItemId
        openAlertsFromIntent = false
    }

    // ================= FCM =================

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

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Listen to AdminMessages so new civilian reports reach the admin app immediately.
    private fun startAdminMessageListener() {
        if (adminMessageRef != null) return

        val ref = FirebaseDatabase.getInstance().getReference("AdminMessages")
        adminMessageRef = ref

        // Prime the listener with existing message keys so only brand-new admin reports raise alerts.
        ref.get()
            .addOnSuccessListener { snapshot ->
                knownAdminMessageKeys.clear()
                snapshot.children.mapNotNullTo(knownAdminMessageKeys) { it.key }
                attachAdminMessageListener(ref)
            }
            .addOnFailureListener {
                // If the warm-up read fails, still attach the live listener so future reports are not missed.
                knownAdminMessageKeys.clear()
                attachAdminMessageListener(ref)
            }
    }

    private fun attachAdminMessageListener(ref: DatabaseReference) {
        if (adminMessageListener != null) return

        adminMessageListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageKey = snapshot.key.orEmpty()
                if (messageKey.isNotBlank() && !knownAdminMessageKeys.add(messageKey)) return

                val alert = buildAdminMessageAlert(snapshot)

                AlertStorage.addAlert(this@DashboardActivity, alert)
                AlertNotifier.show(this@DashboardActivity, alert)
                Toast.makeText(
                    this@DashboardActivity,
                    "NEW REPORT: ${alert.title}\nID: ${alert.complaintDisplayId.ifBlank { "N/A" }}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) = Unit
        }

        ref.addChildEventListener(adminMessageListener as ChildEventListener)
    }

    private fun buildAdminMessageAlert(snapshot: DataSnapshot): AlertItem {
        // Support the civilian-side field names exactly as shared, including older typo variants.
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

    private fun DataSnapshot.readAdminMessageValue(childKey: String): String =
        child(childKey).value?.toString()?.trim().orEmpty()

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

    override fun onDestroy() {
        super.onDestroy()
        stopAdminMessageListener()
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
