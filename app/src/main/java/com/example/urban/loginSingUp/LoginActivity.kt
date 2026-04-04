package com.example.urban.loginSingUp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.urban.bottomNavigation.DashboardActivity
import com.example.urban.databinding.ActivityLoginBinding
import com.example.urban.databinding.DialogForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Restore Remember Me
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("saved_email", "")
        val rememberMe = prefs.getBoolean("remember_me", false)

        if (rememberMe && !savedEmail.isNullOrEmpty()) {
            binding.tilEmail.editText!!.setText(savedEmail)
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

    private fun loginUser() {
        val email = binding.tilEmail.editText!!.text.toString().trim()
        val password = binding.tilPassword.editText!!.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter email & password")
            return
        }

        // Save or clear Remember Me preference
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        if (binding.cbRemember.isChecked) {
            editor.putString("saved_email", email)
            editor.putBoolean("remember_me", true)
        } else {
            editor.remove("saved_email")
            editor.putBoolean("remember_me", false)
        }
        editor.apply()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                toast(e.message ?: "Login failed")
            }
    }

    private fun showForgotPasswordDialog() {
        // Use View Binding for the dialog layout
        val dialogBinding = DialogForgotPasswordBinding.inflate(LayoutInflater.from(this))

        // Pre-fill email if user already typed one
        val currentEmail = binding.tilEmail.editText!!.text.toString().trim()
        if (currentEmail.isNotEmpty()) {
            dialogBinding.tilForgotEmail.editText!!.setText(currentEmail)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setView(dialogBinding.root)
            .setPositiveButton("Send Reset Email", null) // null to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            // Override positive button AFTER show so we can prevent dismiss on empty email
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = dialogBinding.tilForgotEmail.editText!!.text.toString().trim()

                if (email.isEmpty()) {
                    dialogBinding.tilForgotEmail.error = "Please enter your email"
                    return@setOnClickListener
                }

                dialogBinding.tilForgotEmail.error = null

                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        toast("Reset email sent! Check your inbox.")
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        dialogBinding.tilForgotEmail.error = e.message ?: "Failed to send reset email"
                    }
            }
        }

        dialog.show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}


//package com.example.urban.loginSingUp
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.example.urban.bottomNavigation.DashboardActivity
//import com.example.urban.databinding.ActivityLoginBinding
//import com.google.firebase.auth.FirebaseAuth
//
//class LoginActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityLoginBinding
//    private lateinit var auth: FirebaseAuth
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        binding = ActivityLoginBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        auth = FirebaseAuth.getInstance()
//
//        binding.btnLogin.setOnClickListener {
//            loginUser()
//        }
//
//        binding.btnSignIn.setOnClickListener {
//            startActivity(Intent(this, SingUpActivity::class.java))
//        }
//
//        // Forgot Password click
//        binding.btnForgot.setOnClickListener {
//            showForgotPasswordDialog()
//        }
//
//// Remember Me — restore saved email
//        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
//        val savedEmail = prefs.getString("saved_email", "")
//        val rememberMe = prefs.getBoolean("remember_me", false)
//
//        if (rememberMe && !savedEmail.isNullOrEmpty()) {
//            binding.tilEmail.editText!!.setText(savedEmail)
//            binding.cbRemember.isChecked = true
//        }
//
//    }
//
//    private fun loginUser() {
//
//        val email = binding.tilEmail.editText!!.text.toString().trim()
//        val password = binding.tilPassword.editText!!.text.toString().trim()
//
//        if (email.isEmpty() || password.isEmpty()) {
//            toast("Enter email & password")
//            return
//        }
//
//        // Save or clear Remember Me
//        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
//        if (binding.cbRemember.isChecked) {
//            prefs.edit()
//                .putString("saved_email", email)
//                .putBoolean("remember_me", true)
//                .apply()
//        } else {
//            prefs.edit()
//                .remove("saved_email")
//                .putBoolean("remember_me", false)
//                .apply()
//        }
//
//
//        auth.signInWithEmailAndPassword(email, password)
//            .addOnSuccessListener {
//                startActivity(Intent(this, DashboardActivity::class.java))
//                finish()
//            }
//            .addOnFailureListener {
//                toast(it.message ?: "Login failed")
//            }
//    }
//
//    private fun toast(msg: String) {
//        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
//    }
//
//
//    private fun showForgotPasswordDialog() {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
//        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilForgotEmail)
//
//        // Pre-fill email if already typed
//        val currentEmail = binding.tilEmail.editText!!.text.toString().trim()
//        if (currentEmail.isNotEmpty()) {
//            etEmail.editText!!.setText(currentEmail)
//        }
//
//        androidx.appcompat.app.AlertDialog.Builder(this)
//            .setTitle("Reset Password")
//            .setView(dialogView)
//            .setPositiveButton("Send Reset Email") { _, _ ->
//                val email = etEmail.editText!!.text.toString().trim()
//                if (email.isEmpty()) {
//                    toast("Enter your email")
//                    return@setPositiveButton
//                }
//                auth.sendPasswordResetEmail(email)
//                    .addOnSuccessListener {
//                        toast("Reset email sent! Check your inbox.")
//                    }
//                    .addOnFailureListener {
//                        toast(it.message ?: "Failed to send reset email")
//                    }
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//
//}
