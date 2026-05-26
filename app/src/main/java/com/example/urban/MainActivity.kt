package com.example.urban

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.urban.AppLocaleManager
import com.example.urban.loginSingUp.LoginActivity
import com.example.urban.loginSingUp.AppLockManager
import com.example.urban.loginSingUp.SessionManager
import com.example.urban.bottomNavigation.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.urban.loginSingUp.AccountApprovalManager
import com.example.urban.loginSingUp.User

class MainActivity : AppCompatActivity() {

    private val database = FirebaseDatabase.getInstance().reference

    private val appLockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                SessionManager.markUnlocked(this)
                openDashboard()
            } else {
                finish()
            }
        }

    // Shows splash and routes the user.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Restore theme first.
        val prefs = getSharedPreferences("theme", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark", false)
        AppLocaleManager.applySavedLocale(this)

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logo = findViewById<ImageView>(R.id.logo)

        // Small splash animation.
        logo.scaleX = 0.96f
        logo.scaleY = 0.96f
        logo.alpha = 0f

        logo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator())
            .start()

        // Open dashboard or login after splash.
        Handler(Looper.getMainLooper()).postDelayed({
            routeAfterSplash()

        }, 1500)
    }

    // Decides whether to go to login, unlock, or open the dashboard.
    private fun routeAfterSplash() {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra(SessionManager.EXTRA_SESSION_EXPIRED, false)
            })
            finish()
            return
        }

        database.child("Users").child(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val userRecord = snapshot.getValue(User::class.java)
                if (!snapshot.exists() || userRecord == null) {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clear(this)
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        putExtra(SessionManager.EXTRA_SESSION_EXPIRED, false)
                    })
                    finish()
                    return@addOnSuccessListener
                }

                val status = AccountApprovalManager.effectiveStatus(userRecord?.accountStatus)

                when (status) {
                    AccountApprovalManager.STATUS_APPROVED -> continueWithUnlockFlow()
                    AccountApprovalManager.STATUS_PENDING,
                    AccountApprovalManager.STATUS_REJECTED -> {
                        FirebaseAuth.getInstance().signOut()
                        SessionManager.clear(this)
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            putExtra(SessionManager.EXTRA_SESSION_EXPIRED, false)
                        })
                        finish()
                    }
                }
            }
            .addOnFailureListener {
                FirebaseAuth.getInstance().signOut()
                SessionManager.clear(this)
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    putExtra(SessionManager.EXTRA_SESSION_EXPIRED, false)
                })
                finish()
            }
    }

    // This continues the old unlock-or-dashboard flow after account status is confirmed.
    private fun continueWithUnlockFlow() {
        if (SessionManager.isAppLockRequired(this)) {
            val unlockIntent = AppLockManager.createUnlockIntent(this)
            if (unlockIntent != null) {
                appLockLauncher.launch(unlockIntent)
            } else {
                SessionManager.markUnlocked(this)
                openDashboard()
            }
            return
        }

        openDashboard()
    }

    // Opens the dashboard for already signed-in users.
    private fun openDashboard() {
        SessionManager.refreshActivity(this)
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
