package com.example.urban.bottomNavigation

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.bottomNavigation.alert.AlertsFragment
import com.example.urban.bottomNavigation.complaint.ComplaintFragment
import com.example.urban.bottomNavigation.drawer.FO.FieldOfficerFragment
import com.example.urban.bottomNavigation.home.HomeFragment
import com.example.urban.bottomNavigation.map.MapFragment
import com.example.urban.bottomNavigation.profile.ProfileFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

class DashboardActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navDrawer: NavigationView
    private lateinit var toolbar: MaterialToolbar

    private var currentFragment: Fragment? = null

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // ================= INIT =================
        bottomNav = findViewById(R.id.bottomNav)
        drawerLayout = findViewById(R.id.drawerLayout)
        navDrawer = findViewById(R.id.navDrawer)
        toolbar = findViewById(R.id.topBar)

        setSupportActionBar(toolbar)

        toolbar.setNavigationOnClickListener {
            android.widget.Toast.makeText(this, "Clicked", android.widget.Toast.LENGTH_SHORT).show()
        }

        // ================= LOAD DEFAULT SCREEN =================
        loadFragment(HomeFragment())

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
            is ProfileFragment -> toolbar.title = " Your Profile"
            is ComplaintFragment -> toolbar.title = "Complaints"
            is AlertsFragment -> toolbar.title = "Notifications"
            is MapFragment -> toolbar.title = "Map"
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

    menu.clear() // IMPORTANT (prevents old menu bug)

    if (currentFragment is ProfileFragment) {
        menuInflater.inflate(R.menu.profile_menu, menu)
    }

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

        if (role == "Field Officer") {
            menu.findItem(R.id.nav_home).isVisible = false
        }
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

        return when (item.itemId) {

            R.id.menu_settings -> {
                android.widget.Toast.makeText(this, "Settings Clicked", android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            R.id.menu_help -> {
                android.widget.Toast.makeText(this, "Help Clicked", android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
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