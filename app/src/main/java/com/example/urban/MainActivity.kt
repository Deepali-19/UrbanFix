package com.example.urban

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.urban.AppLocaleManager
import com.example.urban.loginSingUp.LoginActivity
import com.example.urban.loginSingUp.SessionManager
import com.example.urban.bottomNavigation.DashboardActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        // 🌙 Restore dark/light mode BEFORE UI loads (IMPORTANT)
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

        // A subtle scale-in works better for a full-screen splash artwork than the old logo pop.
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

        // ⏳ Auto-login check
        Handler(Looper.getMainLooper()).postDelayed({

            val user = FirebaseAuth.getInstance().currentUser

            if (user != null && !SessionManager.isExpired(this)) {
                SessionManager.refreshActivity(this)
                startActivity(Intent(this, DashboardActivity::class.java))
            } else {
                if (user != null) {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clear(this)
                }
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    putExtra(SessionManager.EXTRA_SESSION_EXPIRED, user != null)
                    if (user != null) {
                        putExtra(
                            SessionManager.EXTRA_SESSION_MESSAGE,
                            getString(R.string.session_expired_login_again)
                        )
                    }
                })
            }

            finish()

        }, 1500)
    }
}
