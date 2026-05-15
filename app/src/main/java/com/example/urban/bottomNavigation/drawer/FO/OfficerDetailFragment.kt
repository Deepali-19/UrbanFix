package com.example.urban.bottomNavigation.drawer.FO

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urban.R
import com.example.urban.bottomNavigation.complaint.Complaint
import com.example.urban.bottomNavigation.complaint.ComplaintAdapter
import com.example.urban.bottomNavigation.complaint.ComplaintDetailFragment
import com.example.urban.bottomNavigation.complaint.ComplaintSnapshotParser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView


class OfficerDetailFragment : Fragment(R.layout.fragment_officer_detail) {

    private lateinit var loadingContainer: View
    private lateinit var emptyView: View
    private lateinit var tvName: TextView
    private lateinit var tvDept: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvOfficerComplaintSummary: TextView
    private lateinit var imgProfile: CircleImageView
    private lateinit var recyclerView: RecyclerView

    private val database = FirebaseDatabase.getInstance().reference

    private var officerId: String? = null
    private var profileImageUrl = ""
    private val complaintList = ArrayList<Complaint>()
    private lateinit var adapter: ComplaintAdapter
    private var isOfficerLoaded = false
    private var isComplaintsLoaded = false
    private var isViewReady = false

    // Sets up the officer detail screen.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isViewReady = true
        loadingContainer = view.findViewById(R.id.officerDetailLoadingContainer)
        emptyView = view.findViewById(R.id.tvOfficerComplaintEmpty)
        tvName = view.findViewById(R.id.tvName)
        tvDept = view.findViewById(R.id.tvDept)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvOfficerComplaintSummary = view.findViewById(R.id.tvOfficerComplaintSummary)
        imgProfile = view.findViewById(R.id.imgProfile)
        recyclerView = view.findViewById(R.id.recyclerComplaints)

        officerId = arguments?.getString("officerId")
        showLoading(true)

        adapter = ComplaintAdapter(mutableListOf()) { complaint ->
            val complaintKey = complaint.firebaseKey.ifBlank { complaint.complaintId }
            if (complaintKey.isNotBlank() && canUseUi() && !parentFragmentManager.isStateSaved) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ComplaintDetailFragment.newInstance(complaintKey))
                    .addToBackStack(null)
                    .commit()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        imgProfile.setOnClickListener {
            if (profileImageUrl.isNotBlank()) {
                showProfilePreview(profileImageUrl)
            }
        }

        loadOfficerDetails()
        loadComplaints()
    }

    // Loads officer profile details.
    private fun loadOfficerDetails() {
        val targetOfficerId = officerId ?: return
        database.child("Users").child(targetOfficerId)
            .get()
            .addOnSuccessListener {
                if (!canUseUi()) return@addOnSuccessListener

                val name = it.child("name").value.toString()
                val dept = it.child("department").value.toString()
                val phone = it.child("phone").value.toString()
                val employeeId = it.child("employeeId").value.toString()
                val city = it.child("city").value.toString()
                val image = it.child("profileImageUrl").value?.toString()
                val metaText = when {
                    phone.isNotBlank() -> "Phone: $phone"
                    employeeId.isNotBlank() -> "Employee ID: $employeeId"
                    city.isNotBlank() -> "City: $city"
                    else -> "Details not available"
                }

                tvName.text = name
                tvDept.text = "Department: $dept"
                tvPhone.text = metaText
                isOfficerLoaded = true
                updateLoadingState()

                if (!image.isNullOrEmpty()) {
                    val context = context ?: return@addOnSuccessListener
                    profileImageUrl = image
                    Glide.with(context).load(image).into(imgProfile)
                } else {
                    profileImageUrl = ""
                    imgProfile.setImageResource(R.drawable.users)
                }
            }
            .addOnFailureListener {
                if (!canUseUi()) return@addOnFailureListener
                isOfficerLoaded = true
                updateLoadingState()
            }
    }

    // Opens the officer profile photo in a zoomable full-screen preview.
    private fun showProfilePreview(imageUrl: String) {
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

    // Loads assigned complaints.
    private fun loadComplaints() {

        database.child("Complaints")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!canUseUi()) return
                    complaintList.clear()

                    for (data in ComplaintSnapshotParser.complaintSnapshots(snapshot)) {
                        val complaint = ComplaintSnapshotParser.fromSnapshot(data) ?: continue
                        if (complaint.allottedOfficerId == officerId) {
                            complaintList.add(complaint)
                        }
                    }

                    complaintList.sortByDescending { it.timestamp }
                    adapter.updateList(complaintList)
                    updateComplaintSummary()
                    isComplaintsLoaded = true
                    updateLoadingState()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!canUseUi()) return
                    updateComplaintSummary()
                    isComplaintsLoaded = true
                    updateLoadingState()
                }
            })
    }

    // Updates the complaint summary.
    private fun updateComplaintSummary() {
        if (!canUseUi()) return
        val assignedCount = complaintList.size
        val activeCount = complaintList.count { it.status == 1 }
        val resolvedCount = complaintList.count { it.status == 2 }
        tvOfficerComplaintSummary.text =
            "Assigned $assignedCount • Active $activeCount • Resolved $resolvedCount"
        emptyView.visibility = if (assignedCount == 0) View.VISIBLE else View.GONE
    }

    // Hides loader when both calls finish.
    private fun updateLoadingState() {
        if (!canUseUi()) return
        showLoading(!(isOfficerLoaded && isComplaintsLoaded))
    }

    // Toggles loading state.
    private fun showLoading(isLoading: Boolean) {
        if (!canUseUi()) return
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        if (isLoading) {
            emptyView.visibility = View.GONE
        }
    }

    // Clears the ready flag when the screen view is removed.
    override fun onDestroyView() {
        isViewReady = false
        super.onDestroyView()
    }

    // Returns true only when it is still safe to update this screen.
    private fun canUseUi(): Boolean {
        return isAdded && isViewReady
    }

    companion object {

        // Creates the fragment with the officer id.
        fun newInstance(id: String): OfficerDetailFragment {

            val fragment = OfficerDetailFragment()
            val bundle = Bundle()
            bundle.putString("officerId", id)
            fragment.arguments = bundle
            return fragment
        }
    }
}
