package com.example.urban.loginSingUp

import android.content.Intent
import android.database.Cursor
import android.text.Editable
import android.text.TextWatcher
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.urban.AppConfig
import com.bumptech.glide.Glide
import com.example.urban.AppLocaleManager
import com.example.urban.R
import com.example.urban.BuildConfig
import com.example.urban.databinding.ActivitySingUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
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
    private var selectedIdUri: Uri? = null
    private var selectedIdName = ""
    private lateinit var cityAdapter: ArrayAdapter<String>
    private var citySuggestionJob: Job? = null
    private var citySessionToken = UUID.randomUUID().toString()
    private val fallbackCities by lazy {
        resources.getStringArray(R.array.indian_city_options).toList()
    }

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                selectedIdUri = uri
                selectedIdName = resolveFileName(uri)
                updateUploadedIdUi(
                    visible = true,
                    displayName = if (selectedIdName.isNotBlank()) {
                        selectedIdName
                    } else {
                        getString(R.string.signup_id_uploading)
                    }
                )
                val file = uriToFile(uri)
                startUpload(file)
            }
        }

    // Sets up the signup screen.
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
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

        binding.btnViewUploadedId.setOnClickListener {
            openOfficialIdPreview()
        }
    }

    // Sets up role and department dropdowns.
    private fun setupDropdowns() {

        val roles = listOf("Super Admin", "Department Admin", "Field Officer")
        val departments = listOf("Water", "Roads", "Sanitation", "Electricity")

        binding.actRole.setAdapter(
            ArrayAdapter(this, R.layout.item_signup_dropdown, roles)
        )

        binding.actDepartment.setAdapter(
            ArrayAdapter(this, R.layout.item_signup_dropdown, departments)
        )

        cityAdapter = ArrayAdapter(this, R.layout.item_signup_dropdown, mutableListOf())
        binding.actCity.setAdapter(cityAdapter)

        binding.actRole.keyListener = null
        binding.actDepartment.keyListener = null

        binding.actRole.setOnClickListener { binding.actRole.showDropDown() }
        binding.actDepartment.setOnClickListener { binding.actDepartment.showDropDown() }
        binding.actCity.setOnClickListener {
            if (binding.actCity.text.isNullOrBlank()) {
                showFallbackCities()
            }
        }
        binding.actCity.setOnItemClickListener { _, _, _, _ ->
            citySessionToken = UUID.randomUUID().toString()
        }
        binding.actCity.addTextChangedListener(citySearchWatcher)

        binding.actRole.setOnItemClickListener { _, _, _, _ ->
            updateDepartmentVisibility(binding.actRole.text?.toString().orEmpty())
        }

        updateDepartmentVisibility(binding.actRole.text?.toString().orEmpty())
    }

    // Watches the city box and starts loading live suggestions as the user types.
    private val citySearchWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val query = s?.toString()?.trim().orEmpty()
            citySuggestionJob?.cancel()

            if (query.isBlank()) {
                citySessionToken = UUID.randomUUID().toString()
                return
            }

            if (query.length < 2) {
                showFallbackCities(query)
                return
            }

            citySuggestionJob = lifecycleScope.launch {
                delay(350)
                loadCitySuggestions(query)
            }
        }

        override fun afterTextChanged(s: Editable?) = Unit
    }

    // Loads city suggestions from Google and falls back to the local list if needed.
    private suspend fun loadCitySuggestions(query: String) {
        val apiKey = AppConfig.mapsApiKey
        val apiSuggestions = GoogleCitySuggestionService.fetchCitySuggestions(
            apiKey = apiKey,
            query = query,
            sessionToken = citySessionToken,
            languageCode = Locale.getDefault().language.ifBlank { "en" }
        )

        val finalSuggestions = linkedSetOf<String>().apply {
            addAll(apiSuggestions)
            addAll(filterFallbackCities(query))
        }.toList().take(20)

        if (finalSuggestions.isEmpty()) {
            updateCitySuggestions(emptyList())
            return
        }

        val currentText = binding.actCity.text?.toString()?.trim().orEmpty()
        if (currentText != query) {
            return
        }

        updateCitySuggestions(finalSuggestions)
    }

    // Updates the city dropdown without changing the rest of the signup UI.
    private fun updateCitySuggestions(items: List<String>) {
        cityAdapter.clear()
        cityAdapter.addAll(items)
        cityAdapter.notifyDataSetChanged()

        if (items.isNotEmpty() && binding.actCity.hasFocus()) {
            binding.actCity.showDropDown()
        } else {
            binding.actCity.dismissDropDown()
        }
    }

    // Shows the saved local city list when the field is empty or the API is unavailable.
    private fun showFallbackCities(query: String = "") {
        val items = if (query.isBlank()) {
            fallbackCities.take(15)
        } else {
            filterFallbackCities(query).take(20)
        }
        updateCitySuggestions(items)
    }

    // Keeps the local city list useful by putting the closest text matches first.
    private fun filterFallbackCities(query: String): List<String> {
        val startsWithMatches = fallbackCities.filter { it.startsWith(query, ignoreCase = true) }
        val containsMatches = fallbackCities.filter {
            !it.startsWith(query, ignoreCase = true) && it.contains(query, ignoreCase = true)
        }
        return startsWithMatches + containsMatches
    }

    // Hides department for Super Admin.
    private fun updateDepartmentVisibility(role: String) {
        val isSuperAdmin = role == "Super Admin"
        binding.tilDepartment.visibility = if (isSuperAdmin) View.GONE else View.VISIBLE
        if (isSuperAdmin) {
            binding.actDepartment.setText("", false)
            binding.tilDepartment.error = null
        }
    }

    // Password rule check.
    private fun isValidPassword(password: String): Boolean {
        val pattern =
            Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$")
        return pattern.matches(password)
    }

    // Validates the form and creates the account.
    private fun registerUser() {

        val name = binding.tilName.editText!!.text.toString().trim()
        val email = binding.tilEmail.editText!!.text.toString().trim()
        val password = binding.tilPassword.editText!!.text.toString().trim()
        val confirm = binding.tilConfirmPassword.editText!!.text.toString().trim()
        val role = binding.actRole.text.toString().trim()
        val department = binding.actDepartment.text.toString().trim()
        val city = binding.actCity.text.toString().trim()
        val employeeId = binding.tilEmployeeId.editText!!.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            toast(getString(R.string.fill_required_fields))
            return
        }

        if (role.isEmpty()) {
            toast(getString(R.string.select_role))
            return
        }

        if (role != "Super Admin" && department.isEmpty()) {
            toast(getString(R.string.select_department))
            return
        }

        if (city.isEmpty() || employeeId.isEmpty()) {
            toast(getString(R.string.add_city_employee_id))
            return
        }

        if (!isValidPassword(password)) {
            binding.tilConfirmPassword.error =
                getString(R.string.password_rule_error)
            return
        }

        if (password != confirm) {
            binding.tilConfirmPassword.error = getString(R.string.password_mismatch)
            return
        } else {
            binding.tilConfirmPassword.error = null
        }

        if (uploadedImageUrl.isEmpty()) {
            toast(getString(R.string.upload_id_proof_first))
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
                    department = if (role == "Super Admin") "All Departments" else department,
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

                auth.signOut()
                SessionManager.clear(this)
                toast(getString(R.string.account_created_successfully))

                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                toast(it.message ?: getString(R.string.signup_failed))
            }
    }

    // Uploads the ID proof image.
    private fun startUpload(file: File) {
        lifecycleScope.launch {
            try {
                val bucketId = BuildConfig.APPWRITE_BUCKET_ID
                if (bucketId.isBlank()) {
                    toast("Missing Appwrite upload configuration")
                    return@launch
                }
                val result = appwriteManager.uploadImage(bucketId, file)

                uploadedImageUrl = AppwriteManager.buildFileViewUrl(
                    fileId = result.id,
                    bucketId = result.bucketId
                )

                updateUploadedIdUi(
                    visible = true,
                    displayName = if (selectedIdName.isNotBlank()) {
                        selectedIdName
                    } else {
                        getString(R.string.signup_id_selected_placeholder)
                    }
                )
                toast(getString(R.string.signup_id_uploaded_successfully))

            } catch (e: Exception) {
                updateUploadedIdUi(visible = false, displayName = "")
                selectedIdUri = null
                selectedIdName = ""
                toast(e.message ?: getString(R.string.signup_id_upload_failed))
            }
        }
    }

    // Copies picked image into cache.
    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File(cacheDir, "image.jpg")
        val outputStream = FileOutputStream(tempFile)

        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        return tempFile
    }

    // Shows the selected file name and the preview action after upload.
    private fun updateUploadedIdUi(visible: Boolean, displayName: String) {
        binding.cardUploadedIdInfo.visibility = if (visible) View.VISIBLE else View.GONE
        binding.tvUploadedIdName.text = displayName
        binding.btnViewUploadedId.isEnabled = visible
    }

    // Opens a full screen preview of the selected or uploaded official ID image.
    private fun openOfficialIdPreview() {
        val imageSource = selectedIdUri ?: uploadedImageUrl.takeIf { it.isNotBlank() }
        if (imageSource == null) {
            toast(getString(R.string.signup_no_uploaded_id_preview))
            return
        }

        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_complaint_image_preview)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val imageView = dialog.findViewById<ImageView>(R.id.imgComplaintPreview)
        val closeButton = dialog.findViewById<ImageView>(R.id.btnClosePreview)

        Glide.with(this)
            .load(imageSource)
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .into(imageView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Reads a display name from the picked file so the user can see which ID was selected.
    private fun resolveFileName(uri: Uri): String {
        var name = ""
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex).orEmpty()
            }
        }
        return name
    }

    // Small toast helper.
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        citySuggestionJob?.cancel()
        super.onDestroy()
    }
}
