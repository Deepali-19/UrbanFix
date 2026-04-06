package com.example.urban.bottomNavigation.profile

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.loginSingUp.AppwriteManager
import com.example.urban.loginSingUp.LoginActivity
import com.example.urban.loginSingUp.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment(R.layout.fragment_profile){
    private lateinit var profileImage: CircleImageView
    private lateinit var cameraIcon: ImageView
    private lateinit var loadingContainer: View
    private lateinit var contentScroll: View
//    private lateinit var toolbar: Toolbar

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvEmployeeId: TextView
    private lateinit var btnLogout: Button

    private lateinit var tvOfficialId: TextView

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val appwriteManager by lazy {
        AppwriteManager.getInstance(requireContext())
    }

    private var tempCameraUri: Uri? = null

    // Gallery Picker
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImage(uri) }
        }

    // Gallery Picker
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) {
                uploadImage(tempCameraUri!!)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        loadingContainer = view.findViewById(R.id.profileLoadingContainer)
        contentScroll = view.findViewById(R.id.profileContentScroll)
        profileImage = view.findViewById(R.id.profileImage)
        cameraIcon = view.findViewById(R.id.cameraIcon)
//        toolbar = view.findViewById(R.id.profileToolbar)

        tvName = view.findViewById(R.id.tvName)
        tvEmail = view.findViewById(R.id.tvEmail)
        tvRole = view.findViewById(R.id.tvRole)
        tvDepartment = view.findViewById(R.id.tvDepartment)
        tvCity = view.findViewById(R.id.tvCity)
        tvEmployeeId = view.findViewById(R.id.tvEmployeeId)
        btnLogout = view.findViewById(R.id.btnLogout)
        tvOfficialId = view.findViewById(R.id.tvOfficialId)


//        setupToolbar()
        loadUserData()

        cameraIcon.setOnClickListener { showImagePickerDialog() }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    // Toolbar Menu
//    private fun setupToolbar() {
//        toolbar.setOnMenuItemClickListener {
//            when (it.itemId) {
//                R.id.menu_settings -> {
//                    Toast.makeText(requireContext(), "Settings Clicked", Toast.LENGTH_SHORT).show()
//                    true
//                }
//                R.id.menu_help -> {
//                    Toast.makeText(requireContext(), "Help Clicked", Toast.LENGTH_SHORT).show()
//                    true
//                }
//                else -> false
//            }
//        }
//    }

    // Image Picker Dialog
    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
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

    // Upload to Appwrite + Save URL to Firebase
    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            val file = uriToFile(uri)
            val bucketID = "6999717e003beb1ccaba"
            val result = appwriteManager.uploadImage(bucketID, file)

            val imageUrl =
                "https://fra.cloud.appwrite.io/v1/storage/buckets/${result.bucketId}/files/${result.id}/view?project=699971230022a191cce2"

            val uid = auth.currentUser?.uid ?: return@launch
            database.child("Users").child(uid)
                .child("profileImageUrl")
                .setValue(imageUrl)

            Glide.with(requireContext()).load(imageUrl).into(profileImage)
        }
    }

    // Load User Data

    private fun loadUserData() {

        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        database.child("Users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    showLoading(false)

                    if (!snapshot.exists()) return

                    val user = snapshot.getValue(User::class.java) ?: return

                    tvName.text = user.name
                    tvEmail.text = user.email
                    tvRole.text = "Role: ${user.role}"
                    tvDepartment.text = "Department: ${user.department}"
                    tvCity.text = "City: ${user.city}"
                    tvEmployeeId.text = "Employee ID: ${user.employeeId}"

                    // Profile Image
                    if (user.profileImageUrl.isNotEmpty()) {

                        Glide.with(requireContext())
                            .load(user.profileImageUrl)
                            .into(profileImage)

                    } else {

                        profileImage.setImageResource(R.drawable.ic_user_placeholder)
                    }

                    // Official ID
                    if (user.idProofUrl.isNotEmpty()) {

                        tvOfficialId.text = "View Official ID"

                        tvOfficialId.setOnClickListener {

                            val dialog = Dialog(requireContext())
                            dialog.setContentView(R.layout.dialog_id_preview)

                            dialog.window?.setLayout(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT
                            )

                            val image = dialog.findViewById<ImageView>(R.id.imgOfficialId)
                            val close = dialog.findViewById<ImageView>(R.id.btnClose)

                            Glide.with(requireContext())
                                .load(user.idProofUrl)
                                .into(image)

                            close.setOnClickListener {
                                dialog.dismiss()
                            }

                            dialog.show()
                        }

                    } else {

                        tvOfficialId.text = "Official ID Not Uploaded"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)

                    Toast.makeText(
                        requireContext(),
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        contentScroll.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
//    private fun loadUserData() {
//
//        val uid = auth.currentUser?.uid ?: return
//        database.child("users").child(uid)
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//
//                override fun onDataChange(snapshot: DataSnapshot) {
//
//                    val user = snapshot.getValue(User::class.java) ?: User()
//
//                    tvName.text = user.name
//                    tvEmail.text = user.email
//                    tvRole.text = "Role: ${user.role}"
//                    tvDepartment.text = "Department: ${user.department}"
//                    tvCity.text = "City: ${user.city}"
//                    tvEmployeeId.text = "Employee ID: ${user.employeeId}"
//
//                    // Profile Image
//                    if (user.profileImageUrl.isNotEmpty()) {
//                        Glide.with(requireContext())
//                            .load(user.profileImageUrl)
//                            .into(profileImage)
//                    }
//
//                    // Official ID
//                    if (!user.idProofUrl.isNullOrBlank()) {
//
//                        tvOfficialId.text = "View Official ID"
//
//                        tvOfficialId.setOnClickListener {
//
//                            val dialog = Dialog(requireContext())
//                            dialog.setContentView(R.layout.dialog_id_preview)
//
//                            dialog.window?.setLayout(
//                                WindowManager.LayoutParams.MATCH_PARENT,
//                                WindowManager.LayoutParams.MATCH_PARENT
//                            )
//
//                            val image =
//                                dialog.findViewById<ImageView>(R.id.imgOfficialId)
//                            val close =
//                                dialog.findViewById<ImageView>(R.id.btnClose)
//
//                            Glide.with(requireContext())
//                                .load(user.idProofUrl)
//                                .into(image)
//
//                            close.setOnClickListener {
//                                dialog.dismiss()
//                            }
//
//                            dialog.show()
//                        }
//
//                    } else {
//
//                        tvOfficialId.text = "Official ID Not Uploaded"
//                    }
//                }
//
//                // ✅ OUTSIDE onDataChange
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(
//                        requireContext(),
//                        error.message,
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            })
//    }

//        database.child("users").child(uid)
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//
//                override fun onDataChange(snapshot: DataSnapshot) {
//
//                    val user = snapshot.getValue(User::class.java) ?: User()
//
//                    tvName.text = user.name
//                    tvEmail.text = user.email
//                    tvRole.text = "Role: ${user.role}"
//                    tvDepartment.text = "Department: ${user.department}"
//                    tvCity.text = "City: ${user.city}"
//                    tvEmployeeId.text = "Employee ID: ${user.employeeId}"
//
//                    Log.d("Employee id","${user.employeeId}")
//
//                    // Profile Image
//                    if (user.profileImageUrl.isNotEmpty()) {
//                        Glide.with(requireContext())
//                            .load(user.profileImageUrl)
//                            .into(profileImage)
//                    }
//
//                    // Official ID
//                    if (!user.idProofUrl.isNullOrBlank()) {
//
//                        tvOfficialId.text = "View Official ID"
//
//                        tvOfficialId.setOnClickListener {
//
//                            val dialog = Dialog(requireContext())
//                            dialog.setContentView(R.layout.dialog_id_preview)
//                            dialog.window?.setLayout(
//                                WindowManager.LayoutParams.MATCH_PARENT,
//                                WindowManager.LayoutParams.MATCH_PARENT
//                            )
//
//                            val image = dialog.findViewById<ImageView>(R.id.imgOfficialId)
//                            val close = dialog.findViewById<ImageView>(R.id.btnClose)
//
//                            Glide.with(requireContext())
//                                .load(user.idProofUrl)
//                                .into(image)
//
//                            close.setOnClickListener {
//                                dialog.dismiss()
//                            }
//
//                            dialog.show()
//                        }
//
//                    } else {
//
//                        tvOfficialId.text = "Official ID Not Uploaded"
//
//                        tvOfficialId.setOnClickListener {
//                            Toast.makeText(
//                                requireContext(),
//                                "No Official ID Found",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                    }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Toast.makeText(
//                        requireContext(),
//                        error.message,
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//            })
//    }


// Convert Uri to File
    private fun uriToFile(uri: Uri): File {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().cacheDir, "profile.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        return file
}
}
