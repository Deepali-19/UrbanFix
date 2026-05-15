package com.example.urban.loginSingUp

import android.app.KeyguardManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.urban.R

object AppLockManager {

    // Creates the system phone-lock screen intent if the device has secure lock enabled.
    fun createUnlockIntent(activity: AppCompatActivity): Intent? {
        val keyguardManager = activity.getSystemService(KeyguardManager::class.java) ?: return null
        if (!keyguardManager.isKeyguardSecure) return null

        return keyguardManager.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.app_lock_title),
            activity.getString(R.string.app_lock_subtitle)
        )
    }
}
