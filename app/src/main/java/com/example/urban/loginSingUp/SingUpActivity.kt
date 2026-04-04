package com.example.urban.loginSingUp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.urban.databinding.ActivitySingUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream


class SingUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySingUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val appwriteManager by lazy {
        AppwriteManager.getInstance(applicationContext)
    }

    private var uploadedImageUrl = ""

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val file = uriToFile(uri)
                startUpload(file)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySingUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        setupDropdowns()

        binding.btnUploadId.setOnClickListener {
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnCreateAccount.setOnClickListener {
            registerUser()
        }
    }

    private fun setupDropdowns() {

        val roles = listOf("Super Admin", "Department Admin", "Field Officer")
        val departments = listOf("Water", "Roads", "Sanitation", "Electricity")

        binding.actRole.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, roles)
        )

        binding.actDepartment.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, departments)
        )
    }

    private fun isValidPassword(password: String): Boolean {
        val pattern =
            Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$")
        return pattern.matches(password)
    }
    private fun registerUser() {

        val name = binding.tilName.editText!!.text.toString().trim()
        val email = binding.tilEmail.editText!!.text.toString().trim()
        val password = binding.tilPassword.editText!!.text.toString().trim()
        val confirm = binding.tilConfirmPassword.editText!!.text.toString().trim()
        val role = binding.actRole.text.toString().trim()
        val department = binding.actDepartment.text.toString().trim()
        val city = binding.tilCity.editText!!.text.toString().trim()
        val employeeId = binding.tilEmployeeId.editText!!.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            toast("Fill all required fields")
            return
        }

        if (!isValidPassword(password)) {
            binding.tilConfirmPassword.error =
                "*Password must be 8+ characters with uppercase, lowercase, number and special character"
            return
        }

        if (password != confirm) {
            binding.tilConfirmPassword.error = "*Password doesn't match"
            return
        } else {
            binding.tilConfirmPassword.error = null
        }

        if (uploadedImageUrl.isEmpty()) {
            toast("Upload official ID proof first")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                val uid = auth.currentUser!!.uid

                val user = User(
                    uid = uid,
                    name = name,
                    email = email,
                    role = role,
                    department = department,
                    city = city,
                    deviceToken = "",   // will update later from FCM
                    latitude = 0.0,
                    longitude = 0.0,
                    createdAt = System.currentTimeMillis(),
                    employeeId = employeeId,
                    idProofUrl = uploadedImageUrl,
                    profileImageUrl = ""
                )

                database.child("Users")
                    .child(uid)
                    .setValue(user)

                toast("Account created successfully")

                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                toast(it.message ?: "Signup failed")
            }
    }

//    private fun registerUser() {
//
//        val name = binding.tilName.editText!!.text.toString().trim()
//        val email = binding.tilEmail.editText!!.text.toString().trim()
//        val password = binding.tilPassword.editText!!.text.toString().trim()
//        val confirm = binding.tilConfirmPassword.editText!!.text.toString().trim()
//        val role = binding.actRole.text.toString().trim()
//        val department = binding.actDepartment.text.toString().trim()
//        val city = binding.tilCity.editText!!.text.toString().trim()
//        val employeeId = binding.tilEmployeeId.editText!!.text.toString().trim()
//
//        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
//            toast("Fill all required fields")
//            return
//        }
//
//        if (!isValidPassword(password)) {
//            binding.tilConfirmPassword.error =
//                "*Password must be 8+ characters with uppercase, lowercase, number and special character"
//            return
//        }
//
//        if (password != confirm) {
//            binding.tilConfirmPassword.error = "*Password doesn't match"
//            return
//        } else {
//            binding.tilConfirmPassword.error = null
//        }
//
//        if (uploadedImageUrl.isEmpty()) {
//            toast("Upload official ID proof first")
//            return
//        }
//
//        auth.createUserWithEmailAndPassword(email, password)
//            .addOnSuccessListener {
//
//                val uid = auth.currentUser!!.uid
//
//                val user = User(
//                    name,
//                    email,
//                    role,
//                    department,
//                    city,
//                    employeeId,
//                    uploadedImageUrl
//                )
//
//                database.child("users").child(uid).setValue(user)
//
//                toast("Account created successfully")
//
//                startActivity(Intent(this, LoginActivity::class.java))
//                finish()
//            }
//            .addOnFailureListener {
//                toast(it.message ?: "Signup failed")
//            }
//    }
    private fun startUpload(file: File) {
        lifecycleScope.launch {
            try {

                val bucketId = "6999717e003beb1ccaba"
                val projectId = "699971230022a191cce2"
                val endpoint = "https://fra.cloud.appwrite.io/v1"

                val result = appwriteManager.uploadImage(bucketId, file)

                //  CREATE FULL URL
                uploadedImageUrl =
                    "$endpoint/storage/buckets/${result.bucketId}/files/${result.id}/view?project=$projectId"

                toast("ID uploaded successfully")

            } catch (e: Exception) {
                toast(e.message ?: "Upload failed")
            }
        }
    }
//    private fun startUpload(file: File) {
//
//        lifecycleScope.launch {
//            try {
//                val bucketId = "6999717e003beb1ccaba"
//
//                val result = appwriteManager.uploadImage(bucketId, file)
//
//                uploadedImageUrl = result.id
//
//                toast("ID uploaded successfully")
//
//            } catch (e: Exception) {
//                toast(e.message ?: "Upload failed")
//            }
//        }
//    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File(cacheDir, "image.jpg")
        val outputStream = FileOutputStream(tempFile)

        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        return tempFile
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
