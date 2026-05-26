package com.example.urban.loginSingUp

import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.urban.AppLocaleManager
import com.example.urban.R
import com.example.urban.bottomNavigation.DashboardActivity
import com.example.urban.databinding.ActivityLoginBinding
import com.example.urban.databinding.DialogForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREF_LOGIN = "login_prefs"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_ME = "remember_me"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    // Sets up the login screen.
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (intent.getBooleanExtra(SessionManager.EXTRA_SESSION_EXPIRED, false)) {
            toast(
                intent.getStringExtra(SessionManager.EXTRA_SESSION_MESSAGE)
                    ?: getString(R.string.session_expired_login_again)
            )
        }

        // Restore remembered email.
        val prefs = getSharedPreferences(PREF_LOGIN, MODE_PRIVATE)
        val savedEmail = prefs.getString(KEY_SAVED_EMAIL, "")
        val savedPassword = prefs.getString(KEY_SAVED_PASSWORD, "")
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)

        if (rememberMe && !savedEmail.isNullOrEmpty()) {
            binding.tilEmail.editText!!.setText(savedEmail)
            binding.tilPassword.editText!!.setText(savedPassword.orEmpty())
            binding.cbRemember.isChecked = true
        }

        binding.btnLogin.setOnClickListener { loginUser() }

        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(this, SingUpActivity::class.java))
        }

        binding.btnForgot.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // Validates login and signs the user in.
    private fun loginUser() {
        val email = binding.tilEmail.editText!!.text.toString().trim()
        val password = binding.tilPassword.editText!!.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast(getString(R.string.enter_email_password))
            return
        }

        // Save or clear Remember Me.
        val prefs = getSharedPreferences(PREF_LOGIN, MODE_PRIVATE)
        val editor = prefs.edit()
        if (binding.cbRemember.isChecked) {
            editor.putString(KEY_SAVED_EMAIL, email)
            editor.putString(KEY_SAVED_PASSWORD, password)
            editor.putBoolean(KEY_REMEMBER_ME, true)
        } else {
            editor.remove(KEY_SAVED_EMAIL)
            editor.remove(KEY_SAVED_PASSWORD)
            editor.putBoolean(KEY_REMEMBER_ME, false)
        }
        editor.apply()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                verifyApprovedAccount()
            }
            .addOnFailureListener { e ->
                toast(e.message ?: getString(R.string.login_failed))
            }
    }

    // This allows only approved accounts into the app after Firebase Auth succeeds.
    private fun verifyApprovedAccount() {
        val uid = auth.currentUser?.uid ?: run {
            toast(getString(R.string.login_failed))
            return
        }

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                if (!snapshot.exists() || user == null) {
                    auth.signOut()
                    SessionManager.clear(this)
                    toast(getString(R.string.login_failed))
                    return@addOnSuccessListener
                }

                val status = AccountApprovalManager.effectiveStatus(user?.accountStatus)

                when (status) {
                    AccountApprovalManager.STATUS_APPROVED -> {
                        SessionManager.markAuthenticated(this)
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    }

                    AccountApprovalManager.STATUS_PENDING -> {
                        auth.signOut()
                        SessionManager.clear(this)
                        toast(getString(R.string.account_pending))
                    }

                    else -> {
                        auth.signOut()
                        SessionManager.clear(this)
                        toast(
                            user.rejectionReason
                                .takeIf { it.isNotBlank() }
                                ?.let { AccountApprovalManager.rejectedMessage(it) }
                                ?: getString(R.string.account_rejected)
                        )
                    }
                }
            }
            .addOnFailureListener {
                auth.signOut()
                SessionManager.clear(this)
                toast(getString(R.string.login_failed))
            }
    }

    // Opens the reset password dialog.
    private fun showForgotPasswordDialog() {
        val dialogBinding = DialogForgotPasswordBinding.inflate(LayoutInflater.from(this))

        // Pre-fill email if available.
        val currentEmail = binding.tilEmail.editText!!.text.toString().trim()
        if (currentEmail.isNotEmpty()) {
            dialogBinding.tilForgotEmail.editText!!.setText(currentEmail)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.forgot_password_send_reset, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.profile_header_end))
            negativeButton.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

            // Keep the dialog open on invalid input.
            positiveButton.setOnClickListener {
                val email = dialogBinding.tilForgotEmail.editText!!.text.toString().trim()

                if (email.isEmpty()) {
                    dialogBinding.tilForgotEmail.error = getString(R.string.please_enter_your_email)
                    return@setOnClickListener
                }

                dialogBinding.tilForgotEmail.error = null

                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        toast(getString(R.string.reset_email_sent))
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        dialogBinding.tilForgotEmail.error = e.message ?: getString(R.string.failed_reset_email)
                    }
            }
        }

        dialog.show()
    }

    // Small toast helper.
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
