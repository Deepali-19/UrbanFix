package com.example.urban.bottomNavigation.profile

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.urban.AppLocaleManager
import com.example.urban.BuildConfig
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.ComplaintDataFormatter
import com.example.urban.loginSingUp.AppwriteManager
import com.example.urban.loginSingUp.LoginActivity
import com.example.urban.loginSingUp.SessionManager
import com.example.urban.loginSingUp.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    companion object {
        private const val PREF_THEME = "theme"
        private const val KEY_DARK_MODE = "dark"
        private const val PREF_PROFILE = "profile_preferences"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_LANGUAGE = "language"
    }

    private lateinit var profileImage: CircleImageView
    private lateinit var cameraIcon: ImageView
    private lateinit var loadingContainer: View
    private lateinit var contentScroll: View

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvJoinedDate: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvEmployeeId: TextView
    private lateinit var tvOfficialId: TextView
    private lateinit var tvThemeValue: TextView
    private lateinit var tvNotificationValue: TextView
    private lateinit var tvLanguageValue: TextView
    private lateinit var btnProfileSettings: MaterialButton
    private lateinit var btnProfileHelp: MaterialButton
    private lateinit var btnLogout: Button

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val appwriteManager by lazy { AppwriteManager.getInstance(requireContext()) }
    private lateinit var themePrefs: SharedPreferences
    private lateinit var profilePrefs: SharedPreferences

    private var tempCameraUri: Uri? = null
    private var currentUser: User? = null

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImage(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) {
                uploadImage(tempCameraUri!!)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        themePrefs = requireContext().getSharedPreferences(PREF_THEME, android.content.Context.MODE_PRIVATE)
        profilePrefs = requireContext().getSharedPreferences(PREF_PROFILE, android.content.Context.MODE_PRIVATE)

        loadingContainer = view.findViewById(R.id.profileLoadingContainer)
        contentScroll = view.findViewById(R.id.profileContentScroll)
        profileImage = view.findViewById(R.id.profileImage)
        cameraIcon = view.findViewById(R.id.cameraIcon)

        tvName = view.findViewById(R.id.tvName)
        tvEmail = view.findViewById(R.id.tvEmail)
        tvRole = view.findViewById(R.id.tvRole)
        tvJoinedDate = view.findViewById(R.id.tvJoinedDate)
        tvDepartment = view.findViewById(R.id.tvDepartment)
        tvCity = view.findViewById(R.id.tvCity)
        tvEmployeeId = view.findViewById(R.id.tvEmployeeId)
        tvOfficialId = view.findViewById(R.id.tvOfficialId)
        tvThemeValue = view.findViewById(R.id.tvThemeValue)
        tvNotificationValue = view.findViewById(R.id.tvNotificationValue)
        tvLanguageValue = view.findViewById(R.id.tvLanguageValue)
        btnProfileSettings = view.findViewById(R.id.btnProfileSettings)
        btnProfileHelp = view.findViewById(R.id.btnProfileHelp)
        btnLogout = view.findViewById(R.id.btnLogout)

        loadPreferenceSummary()
        loadUserData()

        cameraIcon.setOnClickListener { showImagePickerDialog() }
        btnProfileSettings.setOnClickListener { openSettingsDialog() }
        btnProfileHelp.setOnClickListener { openHelpDialog() }
        btnLogout.setOnClickListener { logoutUser() }
    }

    fun openSettingsDialog() {
        if (!isAdded) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_settings, null)
        val switchDarkMode = dialogView.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val switchNotifications = dialogView.findViewById<SwitchMaterial>(R.id.switchNotifications)
        val languageInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.actLanguage)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelSettings)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveSettings)

        val languageOptions = listOf(
            getString(R.string.language_option_english),
            getString(R.string.language_option_hindi),
            getString(R.string.language_option_system)
        )
        languageInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, languageOptions)
        )
        languageInput.keyListener = null
        languageInput.setOnClickListener { languageInput.showDropDown() }

        switchDarkMode.isChecked = themePrefs.getBoolean(KEY_DARK_MODE, false)
        switchNotifications.isChecked = profilePrefs.getBoolean(KEY_NOTIFICATIONS, true)
        val currentLanguageCode = AppLocaleManager.currentLanguageCode(requireContext())
        languageInput.setText(AppLocaleManager.labelForCode(requireContext(), currentLanguageCode), false)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val darkMode = switchDarkMode.isChecked
            val notificationsEnabled = switchNotifications.isChecked
            val selectedLabel = languageInput.text?.toString().orEmpty()
            val languageCode = AppLocaleManager.codeForLabel(requireContext(), selectedLabel)
            val previousLanguageCode = AppLocaleManager.currentLanguageCode(requireContext())

            themePrefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply()
            profilePrefs.edit()
                .putBoolean(KEY_NOTIFICATIONS, notificationsEnabled)
                .apply()
            AppLocaleManager.saveLanguageCode(requireContext(), languageCode)

            AppCompatDelegate.setDefaultNightMode(
                if (darkMode) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            )
            AppLocaleManager.applyLanguageCode(languageCode)

            loadPreferenceSummary()
            dialog.dismiss()
            toast(getString(R.string.profile_settings_saved))

            if (previousLanguageCode != languageCode) {
                requireActivity().recreate()
            }
        }

        dialog.show()
    }

    fun openHelpDialog() {
        if (!isAdded) return

        val user = currentUser
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_help, null)
        val tvHelpBody = dialogView.findViewById<TextView>(R.id.tvHelpBody)
        val tvHelpTipOne = dialogView.findViewById<TextView>(R.id.tvHelpTipOne)
        val tvHelpTipTwo = dialogView.findViewById<TextView>(R.id.tvHelpTipTwo)
        val tvHelpTipThree = dialogView.findViewById<TextView>(R.id.tvHelpTipThree)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseHelp)

        val roleName = user?.role?.ifBlank { "official" } ?: "official"
        tvHelpBody.text = getString(R.string.profile_help_body, roleName)
        tvHelpTipOne.text = getString(R.string.profile_help_tip_one)
        tvHelpTipTwo.text = getString(R.string.profile_help_tip_two)
        tvHelpTipThree.text = getString(R.string.profile_help_tip_three)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun loadPreferenceSummary() {
        val isDarkMode = themePrefs.getBoolean(KEY_DARK_MODE, false)
        val notificationsEnabled = profilePrefs.getBoolean(KEY_NOTIFICATIONS, true)
        val languageCode = AppLocaleManager.currentLanguageCode(requireContext())

        tvThemeValue.text = if (isDarkMode) getString(R.string.theme_dark) else getString(R.string.theme_light)
        tvNotificationValue.text = if (notificationsEnabled) getString(R.string.notifications_enabled) else getString(R.string.notifications_muted)
        tvLanguageValue.text = AppLocaleManager.labelForCode(requireContext(), languageCode)
    }

    private fun showImagePickerDialog() {
        val options = arrayOf(
            getString(R.string.image_picker_take_photo),
            getString(R.string.image_picker_choose_gallery)
        )
        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun openCamera() {
        val file = File(requireContext().cacheDir, "camera.jpg")
        tempCameraUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )
        cameraLauncher.launch(tempCameraUri)
    }

    // Upload and persist the profile image so the dashboard drawer and profile stay in sync.
    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            cameraIcon.isEnabled = false
            try {
                val file = uriToFile(uri)
                val bucketId = BuildConfig.APPWRITE_BUCKET_ID
                if (bucketId.isBlank()) {
                    toast("Missing Appwrite upload configuration")
                    return@launch
                }
                val result = appwriteManager.uploadImage(bucketId, file)
                val imageUrl = AppwriteManager.buildFileViewUrl(
                    fileId = result.id,
                    bucketId = result.bucketId
                )

                val uid = auth.currentUser?.uid ?: return@launch
                database.child("Users").child(uid)
                    .child("profileImageUrl")
                    .setValue(imageUrl)

                Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .into(profileImage)

                currentUser = currentUser?.copy(profileImageUrl = imageUrl)
                toast("Profile photo updated")
            } catch (exception: Exception) {
                toast(exception.message ?: "Profile image upload failed")
            } finally {
                cameraIcon.isEnabled = true
            }
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        database.child("Users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    showLoading(false)

                    if (!snapshot.exists()) return

                    val user = snapshot.getValue(User::class.java) ?: return
                    currentUser = user

                    tvName.text = user.name.ifBlank { "Urban Fix User" }
                    tvEmail.text = user.email.ifBlank { getString(R.string.profile_no_email) }
                    tvRole.text = localizedRoleLabel(user.role)
                    tvJoinedDate.text = formatJoinedDate(user.createdAt)
                    tvDepartment.text = ComplaintDataFormatter.localizedDepartmentName(
                        requireContext(),
                        user.department.ifBlank { "General" }
                    )
                    tvCity.text = user.city.ifBlank { getString(R.string.profile_not_added) }
                    tvEmployeeId.text = user.employeeId.ifBlank { getString(R.string.profile_not_assigned) }

                    if (user.profileImageUrl.isNotBlank()) {
                        Glide.with(requireContext())
                            .load(user.profileImageUrl)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .into(profileImage)
                    } else {
                        profileImage.setImageResource(R.drawable.ic_user_placeholder)
                    }

                    if (user.idProofUrl.isNotBlank()) {
                        tvOfficialId.text = getString(R.string.profile_view_official_id)
                        tvOfficialId.setOnClickListener {
                            showOfficialIdPreview(user.idProofUrl)
                        }
                    } else {
                        tvOfficialId.text = getString(R.string.profile_official_id_not_uploaded)
                        tvOfficialId.setOnClickListener(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                    toast(error.message)
                }
            })
    }

    private fun showOfficialIdPreview(imageUrl: String) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_id_preview)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val image = dialog.findViewById<ImageView>(R.id.imgOfficialId)
        val close = dialog.findViewById<ImageView>(R.id.btnClose)

        Glide.with(requireContext())
            .load(imageUrl)
            .into(image)

        close.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun logoutUser() {
        auth.signOut()
        SessionManager.clear(requireContext())
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        contentScroll.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun formatJoinedDate(timestamp: Long): String {
        if (timestamp <= 0L) return getString(R.string.profile_joined_recently)
        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        return getString(R.string.profile_joined_on, date)
    }

    private fun localizedRoleLabel(role: String): String {
        return when (role) {
            "Super Admin" -> getString(R.string.role_super_admin)
            "Department Admin" -> getString(R.string.role_department_admin)
            "Field Officer" -> getString(R.string.role_field_officer)
            else -> getString(R.string.role_official)
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().cacheDir, "profile.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        return file
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
