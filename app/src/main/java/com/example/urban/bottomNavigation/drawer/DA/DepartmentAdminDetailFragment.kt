package com.example.urban.bottomNavigation.drawer.DA

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.urban.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DepartmentAdminDetailFragment : Fragment(R.layout.fragment_department_admin_detail) {

    private lateinit var loadingContainer: View
    private lateinit var contentContainer: View
    private lateinit var emptyView: View
    private lateinit var imgProfile: CircleImageView
    private lateinit var tvName: TextView
    private lateinit var tvJoined: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvEmployeeId: TextView
    private lateinit var tvCity: TextView
    private lateinit var btnViewId: MaterialButton

    private val database = FirebaseDatabase.getInstance().reference
    private val joinedDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private var adminUid: String? = null
    private var profileImageUrl = ""
    private var idProofUrl = ""
    private var isViewReady = false

    // This prepares the department admin detail screen.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewReady = true
        loadingContainer = view.findViewById(R.id.departmentAdminDetailLoadingContainer)
        contentContainer = view.findViewById(R.id.departmentAdminDetailContent)
        emptyView = view.findViewById(R.id.tvDepartmentAdminDetailEmpty)
        imgProfile = view.findViewById(R.id.imgDepartmentAdminProfile)
        tvName = view.findViewById(R.id.tvDepartmentAdminName)
        tvJoined = view.findViewById(R.id.tvDepartmentAdminJoined)
        tvDepartment = view.findViewById(R.id.tvDepartmentAdminDepartment)
        tvEmail = view.findViewById(R.id.tvDepartmentAdminEmail)
        tvEmployeeId = view.findViewById(R.id.tvDepartmentAdminEmployeeId)
        tvCity = view.findViewById(R.id.tvDepartmentAdminCity)
        btnViewId = view.findViewById(R.id.btnDepartmentAdminViewId)

        adminUid = arguments?.getString(ARG_ADMIN_UID)

        imgProfile.setOnClickListener {
            if (profileImageUrl.isNotBlank()) {
                showImagePreview(profileImageUrl)
            }
        }
        btnViewId.setOnClickListener {
            if (idProofUrl.isNotBlank()) {
                showImagePreview(idProofUrl)
            }
        }

        loadAdminDetails()
    }

    // This loads one department admin profile from Firebase.
    private fun loadAdminDetails() {
        val uid = adminUid ?: run {
            showEmptyState()
            return
        }

        showLoading(true)

        database.child("Users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!canUseUi()) return@addOnSuccessListener
                if (!snapshot.exists()) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                profileImageUrl = snapshot.child("profileImageUrl").value?.toString().orEmpty()
                idProofUrl = snapshot.child("idProofUrl").value?.toString().orEmpty()

                tvName.text = snapshot.child("name").value?.toString().orEmpty().ifBlank { "Department Admin" }
                tvJoined.text = formatJoinedDate(snapshot.child("createdAt").getValue(Long::class.java) ?: 0L)
                tvEmail.text = "Email: ${snapshot.child("email").value?.toString().orEmpty().ifBlank { "Not available" }}"
                tvEmployeeId.text = "Employee ID: ${snapshot.child("employeeId").value?.toString().orEmpty().ifBlank { "Not assigned" }}"
                tvDepartment.text = "Department: ${snapshot.child("department").value?.toString().orEmpty().ifBlank { "General" }}"
                tvCity.text = "City: ${snapshot.child("city").value?.toString().orEmpty().ifBlank { "Not added" }}"

                if (profileImageUrl.isNotBlank()) {
                    Glide.with(requireContext())
                        .load(profileImageUrl)
                        .placeholder(R.drawable.users)
                        .error(R.drawable.users)
                        .into(imgProfile)
                } else {
                    imgProfile.setImageResource(R.drawable.users)
                }

                btnViewId.visibility = if (idProofUrl.isNotBlank()) View.VISIBLE else View.GONE
                contentContainer.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                showLoading(false)
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                showEmptyState()
            }
    }

    // This opens profile image or official ID in the same fullscreen preview style used in the app.
    private fun showImagePreview(imageUrl: String) {
        if (!canUseUi()) return

        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_complaint_image_preview)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val imageView = dialog.findViewById<ImageView>(R.id.imgComplaintPreview)
        val closeButton = dialog.findViewById<ImageView>(R.id.btnClosePreview)

        Glide.with(requireContext())
            .load(imageUrl)
            .placeholder(R.drawable.users)
            .error(R.drawable.users)
            .into(imageView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // This shows the empty state if the profile is not available.
    private fun showEmptyState() {
        if (!canUseUi()) return
        contentContainer.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        showLoading(false)
    }

    // This toggles the loading overlay.
    private fun showLoading(isLoading: Boolean) {
        if (!canUseUi()) return
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // This formats the joining date into a simple readable profile line.
    private fun formatJoinedDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Joined recently"
        return "Joined ${joinedDateFormat.format(Date(timestamp))}"
    }

    override fun onDestroyView() {
        isViewReady = false
        super.onDestroyView()
    }

    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }

    companion object {
        private const val ARG_ADMIN_UID = "admin_uid"

        // This creates the fragment with the department admin uid.
        fun newInstance(uid: String): DepartmentAdminDetailFragment {
            val fragment = DepartmentAdminDetailFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_ADMIN_UID, uid)
            }
            return fragment
        }
    }
}
