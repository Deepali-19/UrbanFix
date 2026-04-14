package com.example.urban.bottomNavigation.complaint

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.urban.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComplaintDetailFragment : Fragment(R.layout.fragment_complaint_detail) {

    private lateinit var database: DatabaseReference

    private lateinit var loadingContainer: View
    private lateinit var contentScroll: View
    private lateinit var tvTitle: TextView
    private lateinit var tvId: TextView
    private lateinit var tvStatusValue: TextView
    private lateinit var tvPriorityValue: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvIssueType: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvEtaValue: TextView
    private lateinit var tvEtaReasonValue: TextView
    private lateinit var tvEtaNotifyStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvCitizenNameValue: TextView
    private lateinit var tvPhoneValue: TextView
    private lateinit var tvAssignedOfficerValue: TextView
    private lateinit var tvValidationValue: TextView
    private lateinit var tvFeedbackValue: TextView
    private lateinit var imgComplaint: ImageView
    private lateinit var btnOpenMap: Button
    private lateinit var btnPreviewImage: Button
    private lateinit var btnNotifyEta: Button

    private lateinit var assignSection: View
    private lateinit var statusSection: View
    private lateinit var spOfficer: Spinner
    private lateinit var actPriority: MaterialAutoCompleteTextView
    private lateinit var cbValidated: CheckBox
    private lateinit var statusGroup: RadioGroup
    private lateinit var etRemark: EditText
    private lateinit var btnAssignOfficer: Button
    private lateinit var btnUpdateStatus: Button

    private var complaintKey: String? = null
    private var currentComplaint: Complaint? = null

    private val officerNames = ArrayList<String>()
    private val officerIds = ArrayList<String>()

    private var userRole = ""
    private var userDepartment = ""
    private var isComplaintLoaded = false
    private var isRoleLoaded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        database = FirebaseDatabase.getInstance().reference

        loadingContainer = view.findViewById(R.id.detailLoadingContainer)
        contentScroll = view.findViewById(R.id.detailContentScroll)
        tvTitle = view.findViewById(R.id.tvComplaintTitle)
        tvId = view.findViewById(R.id.tvComplaintId)
        tvStatusValue = view.findViewById(R.id.tvStatusValue)
        tvPriorityValue = view.findViewById(R.id.tvPriorityValue)
        tvDescription = view.findViewById(R.id.tvDescription)
        tvIssueType = view.findViewById(R.id.tvIssueType)
        tvDepartment = view.findViewById(R.id.tvDepartment)
        tvDate = view.findViewById(R.id.tvDate)
        tvEtaValue = view.findViewById(R.id.tvEtaValue)
        tvEtaReasonValue = view.findViewById(R.id.tvEtaReasonValue)
        tvEtaNotifyStatus = view.findViewById(R.id.tvEtaNotifyStatus)
        tvLocation = view.findViewById(R.id.tvLocation)
        tvCoordinates = view.findViewById(R.id.tvCoordinates)
        tvCitizenNameValue = view.findViewById(R.id.tvCitizenNameValue)
        tvPhoneValue = view.findViewById(R.id.tvPhoneValue)
        tvAssignedOfficerValue = view.findViewById(R.id.tvAssignedOfficerValue)
        tvValidationValue = view.findViewById(R.id.tvValidationValue)
        tvFeedbackValue = view.findViewById(R.id.tvFeedbackValue)
        imgComplaint = view.findViewById(R.id.imgComplaint)
        btnOpenMap = view.findViewById(R.id.btnOpenMap)
        btnPreviewImage = view.findViewById(R.id.btnPreviewImage)
        btnNotifyEta = view.findViewById(R.id.btnNotifyEta)

        assignSection = view.findViewById(R.id.assignSection)
        statusSection = view.findViewById(R.id.statusSection)
        spOfficer = view.findViewById(R.id.spOfficer)
        actPriority = view.findViewById(R.id.actPriority)
        cbValidated = view.findViewById(R.id.cbValidated)
        statusGroup = view.findViewById(R.id.statusGroup)
        etRemark = view.findViewById(R.id.etRemark)
        btnAssignOfficer = view.findViewById(R.id.btnAssignOfficer)
        btnUpdateStatus = view.findViewById(R.id.btnUpdateStatus)

        setupPriorityDropdown()

        complaintKey = arguments?.getString(ARG_COMPLAINT_KEY)
        showLoading(true)

        btnAssignOfficer.setOnClickListener { saveAdminChanges() }
        btnUpdateStatus.setOnClickListener { updateComplaintStatus() }
        btnOpenMap.setOnClickListener { openLocationInMaps() }
        btnPreviewImage.setOnClickListener { previewComplaintImage() }
        imgComplaint.setOnClickListener { previewComplaintImage() }
        btnNotifyEta.setOnClickListener { notifyEtaToCivilian() }

        loadComplaint()
        loadUserRole()
    }

    private fun setupPriorityDropdown() {
        val items = listOf("Low", "Medium", "High")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            items
        )

        actPriority.setAdapter(adapter)
        actPriority.keyListener = null
        actPriority.setOnClickListener { actPriority.showDropDown() }
        actPriority.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                actPriority.showDropDown()
            }
        }
    }

    private fun loadComplaint() {
        val key = complaintKey ?: return
        isComplaintLoaded = false
        updateLoadingState()

        database.child("Complaints")
            .child(key)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val complaint = snapshot.getValue(Complaint::class.java)
                    if (complaint == null) {
                        isComplaintLoaded = true
                        updateLoadingState()
                        Toast.makeText(requireContext(), "Complaint not found", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val hydratedComplaint = hydrateComplaint(snapshot, complaint)
                    hydratedComplaint.firebaseKey = snapshot.key.orEmpty()
                    currentComplaint = hydratedComplaint
                    bindComplaint(hydratedComplaint)
                    maybeMarkComplaintAsRead(hydratedComplaint)
                    isComplaintLoaded = true
                    updateLoadingState()
                }

                override fun onCancelled(error: DatabaseError) {
                    isComplaintLoaded = true
                    updateLoadingState()
                }
            })
    }

    private fun bindComplaint(complaint: Complaint) {
        val displayId = complaint.complaintId.ifBlank {
            complaint.firebaseKey.ifBlank { "N/A" }
        }
        val hasCoordinates = ComplaintDataFormatter.hasCoordinates(complaint)
        val hasImage = complaint.images.isNotEmpty() && complaint.images.first().isNotBlank()

        tvTitle.text = complaint.title.ifBlank { "Complaint Details" }
        tvId.text = "Complaint ID: $displayId"
        tvDescription.text = complaint.description.ifBlank { "No description provided" }
        tvIssueType.text = complaint.issueType.ifBlank { "Not specified" }
        tvDepartment.text = ComplaintDataFormatter.resolvedDepartment(complaint)
        tvEtaValue.text = ComplaintEtaManager.etaDisplayLabel(complaint)
        tvEtaReasonValue.text = ComplaintEtaManager.etaReasonLabel(complaint)
        tvEtaNotifyStatus.text = etaNotifyStatusLabel(complaint)
        tvLocation.text = ComplaintDataFormatter.locationLabel(complaint)
        tvCoordinates.text = ComplaintDataFormatter.coordinatesLabel(complaint)
        tvDate.text = if (complaint.timestamp > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(Date(complaint.timestamp))
        } else {
            "Not available"
        }
        tvPhoneValue.text = if (complaint.phone > 0L) {
            complaint.phone.toString()
        } else {
            "Not available"
        }
        tvCitizenNameValue.text = if (complaint.civilianId.isNotBlank()) {
            "Loading citizen details..."
        } else {
            "Not available"
        }
        tvValidationValue.text = if (complaint.validation) {
            "Verified by admin"
        } else {
            "Pending verification"
        }
        tvFeedbackValue.text = complaint.feedback.ifBlank { "No field remark yet" }
        tvAssignedOfficerValue.text = if (complaint.allottedOfficerId.isBlank()) {
            "Not assigned yet"
        } else {
            "Loading assigned officer..."
        }

        bindStatusBadge(complaint.status)
        bindPriorityBadge(complaint.priority)
        actPriority.setText(priorityLabel(complaint.priority), false)
        cbValidated.isChecked = complaint.validation
        etRemark.setText(complaint.feedback)

        when (complaint.status) {
            0 -> statusGroup.check(R.id.rbPending)
            1 -> statusGroup.check(R.id.rbProgress)
            2 -> statusGroup.check(R.id.rbResolved)
        }

        if (hasImage) {
            imgComplaint.visibility = View.VISIBLE
            Glide.with(requireContext())
                .load(complaint.images.first())
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(imgComplaint)
        } else {
            imgComplaint.visibility = View.GONE
        }

        btnPreviewImage.visibility = if (hasImage) View.VISIBLE else View.GONE
        btnOpenMap.visibility = if (hasCoordinates || complaint.location.isNotBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (complaint.allottedOfficerId.isNotBlank()) {
            loadAssignedOfficerName(complaint.allottedOfficerId)
        }

        if (complaint.civilianId.isNotBlank()) {
            loadCivilianDetails(complaint.civilianId)
        }

        preselectAssignedOfficer(complaint.allottedOfficerId)
        updateEtaActionVisibility()
    }

    private fun hydrateComplaint(snapshot: DataSnapshot, complaint: Complaint): Complaint {
        val timestamp = complaint.timestamp.takeIf { it > 0L }
            ?: snapshot.readLong("timestamp", "timeStamp", "createdAt", "reportedOn")
            ?: 0L
        val phone = complaint.phone.takeIf { it > 0L }
            ?: snapshot.readLong("phone", "mobile", "contact", "citizenPhone")
            ?: 0L
        val latitude = complaint.latitude.takeIf { it != 0.0 }
            ?: snapshot.readDouble("latitude", "lat")
            ?: snapshot.readNestedDouble("location", "latitude")
            ?: snapshot.readNestedDouble("coordinates", "latitude")
            ?: snapshot.readNestedDouble("latLng", "latitude")
            ?: 0.0
        val longitude = complaint.longitude.takeIf { it != 0.0 }
            ?: snapshot.readDouble("longitude", "lng", "lon")
            ?: snapshot.readNestedDouble("location", "longitude")
            ?: snapshot.readNestedDouble("coordinates", "longitude")
            ?: snapshot.readNestedDouble("latLng", "longitude")
            ?: 0.0
        val location = complaint.location.takeIf { it.isNotBlank() }
            ?: snapshot.readString("location", "address", "selectedLocation")
            ?: snapshot.readNestedString("location", "address")
            ?: snapshot.readNestedString("location", "name")
            ?: ""

        return complaint.copy(
            complaintId = complaint.complaintId.ifBlank {
                snapshot.readString("complaintId", "id") ?: ""
            },
            issueType = complaint.issueType.ifBlank {
                snapshot.readString("issueType", "category", "issue") ?: ""
            },
            title = complaint.title.ifBlank {
                snapshot.readString("title", "subject") ?: ""
            },
            description = complaint.description.ifBlank {
                snapshot.readString("description", "details") ?: ""
            },
            civilianId = complaint.civilianId.ifBlank {
                snapshot.readString("civilianId", "userId", "citizenId") ?: ""
            },
            phone = phone,
            location = location,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            departmentId = complaint.departmentId.ifBlank {
                snapshot.readString("departmentId", "department") ?: ""
            }
        )
    }

    private fun loadCivilianDetails(civilianId: String) {
        database.child("Users").child(civilianId)
            .get()
            .addOnSuccessListener { snapshot ->
                val fallbackName = snapshot.readString("name", "username", "fullName", "displayName")
                val fallbackPhone = snapshot.readLong("phone", "mobile", "contact", "citizenPhone")

                if (!fallbackName.isNullOrBlank()) {
                    tvCitizenNameValue.text = fallbackName
                }

                if (fallbackPhone != null && fallbackPhone > 0L) {
                    currentComplaint = currentComplaint?.copy(phone = fallbackPhone)?.also {
                        it.firebaseKey = complaintKey.orEmpty()
                    }
                    tvPhoneValue.text = fallbackPhone.toString()
                }

                if (fallbackName.isNullOrBlank() && (fallbackPhone == null || fallbackPhone <= 0L)) {
                    tvCitizenNameValue.text = "Not available"
                } else if (fallbackName.isNullOrBlank()) {
                    tvCitizenNameValue.text = "Not available"
                }
            }
            .addOnFailureListener {
                tvCitizenNameValue.text = "Not available"
            }
    }

    private fun bindStatusBadge(status: Int) {
        tvStatusValue.text = statusLabel(status)
        when (status) {
            0 -> tvStatusValue.setBackgroundResource(R.drawable.status_pending)
            1 -> tvStatusValue.setBackgroundResource(R.drawable.status_progress)
            else -> tvStatusValue.setBackgroundResource(R.drawable.status_resolved)
        }
    }

    private fun bindPriorityBadge(priority: Int) {
        tvPriorityValue.text = priorityLabel(priority)
        when (priority) {
            2 -> tvPriorityValue.setBackgroundResource(R.drawable.priority_high)
            1 -> tvPriorityValue.setBackgroundResource(R.drawable.priority_medium)
            else -> tvPriorityValue.setBackgroundResource(R.drawable.priority_low)
        }
    }

    private fun updateComplaintStatus() {
        if (userRole != "Field Officer") return

        val key = complaintKey ?: return
        val complaint = currentComplaint ?: return
        val remark = etRemark.text.toString().trim()
        val status = when (statusGroup.checkedRadioButtonId) {
            R.id.rbPending -> 0
            R.id.rbProgress -> 1
            R.id.rbResolved -> 2
            else -> 0
        }
        val now = System.currentTimeMillis()

        // Keep activity timestamps updated so the dashboard can show recent movement.
        val update = hashMapOf<String, Any>(
            "status" to status,
            "feedback" to remark,
            "readByAdmin" to false,
            "updatedAt" to now,
            "resolvedAt" to if (status == 2) now else 0L
        )

        database.child("Complaints")
            .child(key)
            .updateChildren(update)
            .addOnSuccessListener {
                val updatedComplaint = complaint.copy(
                    status = status,
                    feedback = remark,
                    readByAdmin = false,
                    updatedAt = now,
                    resolvedAt = if (status == 2) now else 0L
                ).also {
                    it.firebaseKey = complaint.firebaseKey
                }

                refreshEtaForComplaint(
                    complaintKey = key,
                    baseComplaint = updatedComplaint
                ) { _, _ ->
                    Toast.makeText(requireContext(), "Complaint updated", Toast.LENGTH_SHORT).show()
                    if (status == 2) {
                        sendSMS(complaint.phone, complaint.complaintId.ifBlank { key })
                    }
                    loadComplaint()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), error.message ?: "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveAdminChanges() {
        if (userRole == "Field Officer") return

        val key = complaintKey ?: return
        val complaint = currentComplaint ?: return
        val updates = hashMapOf<String, Any>()
        val now = System.currentTimeMillis()

        val selectedPriority = priorityValueFromLabel(actPriority.text?.toString().orEmpty())
        if (selectedPriority != complaint.priority) {
            updates["priority"] = selectedPriority
        }

        if (cbValidated.isChecked != complaint.validation) {
            updates["validation"] = cbValidated.isChecked
        }

        val selectedPosition = spOfficer.selectedItemPosition
        if (selectedPosition in officerIds.indices) {
            val selectedOfficerId = officerIds[selectedPosition]
            if (selectedOfficerId != complaint.allottedOfficerId) {
                updates["allottedOfficerId"] = selectedOfficerId
            }
        }

        val resolvedDepartment = ComplaintDataFormatter.resolvedDepartment(complaint)
        if (complaint.departmentId.isBlank()) {
            updates["departmentId"] = resolvedDepartment
        }

        if (updates.isEmpty()) {
            Toast.makeText(requireContext(), "No complaint changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        updates["readByAdmin"] = true
        updates["updatedAt"] = now

        database.child("Complaints")
            .child(key)
            .updateChildren(updates)
            .addOnSuccessListener {
                val updatedComplaint = complaint.copy(
                    priority = updates["priority"] as? Int ?: complaint.priority,
                    validation = updates["validation"] as? Boolean ?: complaint.validation,
                    allottedOfficerId = updates["allottedOfficerId"] as? String ?: complaint.allottedOfficerId,
                    departmentId = updates["departmentId"] as? String ?: complaint.departmentId,
                    readByAdmin = true,
                    updatedAt = now
                ).also {
                    it.firebaseKey = complaint.firebaseKey
                }

                refreshEtaForComplaint(
                    complaintKey = key,
                    baseComplaint = updatedComplaint
                ) { _, etaChanged ->
                    val message = if (etaChanged) {
                        "Complaint settings saved and ETA updated"
                    } else {
                        "Complaint settings saved"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    loadComplaint()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), error.message ?: "Save failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openLocationInMaps() {
        val complaint = currentComplaint ?: return
        val hasCoordinates = complaint.latitude != 0.0 || complaint.longitude != 0.0

        val uri = if (hasCoordinates) {
            Uri.parse("geo:0,0?q=${complaint.latitude},${complaint.longitude}(Complaint)")
        } else if (complaint.location.isNotBlank()) {
            Uri.parse("geo:0,0?q=${Uri.encode(complaint.location)}")
        } else {
            null
        }

        if (uri == null) return

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun previewComplaintImage() {
        val imageUrl = currentComplaint?.images?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return

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
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .into(imageView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun sendSMS(phone: Long, complaintId: String) {
        if (phone <= 0L) return

        val message = "Your complaint ($complaintId) has been resolved."
        openSmsComposer(phone, message)
    }

    private fun notifyEtaToCivilian() {
        val complaint = currentComplaint ?: return
        val key = complaintKey ?: return
        if (complaint.civilianId.isBlank()) {
            Toast.makeText(requireContext(), "Civilian account is not linked to this complaint", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val payload = hashMapOf<String, Any>(
            "title" to "Complaint ETA Updated",
            "body" to ComplaintEtaManager.civilianEtaMessage(complaint, key),
            "complaintId" to complaint.complaintId.ifBlank { key },
            "complaintKey" to key,
            "civilianId" to complaint.civilianId,
            "type" to "ETA Update",
            "estimatedResolutionAt" to complaint.estimatedResolutionAt,
            "etaHours" to complaint.etaHours,
            "timestamp" to now
        )

        database.child("CivilianMessages")
            .push()
            .setValue(payload)
            .addOnSuccessListener {
                database.child("Complaints")
                    .child(key)
                    .child("etaNotificationSentAt")
                    .setValue(now)

                currentComplaint = complaint.copy(etaNotificationSentAt = now).also {
                    it.firebaseKey = complaint.firebaseKey
                }
                tvEtaNotifyStatus.text = etaNotifyStatusLabel(currentComplaint ?: complaint)
                Toast.makeText(requireContext(), "ETA notification sent to civilian app", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), error.message ?: "Failed to notify civilian", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openSmsComposer(phone: Long, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", message)
        }

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun loadUserRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isRoleLoaded = false
        updateLoadingState()

        database.child("Users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userRole = snapshot.child("role").value.toString()
                    userDepartment = snapshot.child("department").value.toString()

                    configureUI()
                    currentComplaint?.let { maybeMarkComplaintAsRead(it) }
                    isRoleLoaded = true
                    updateLoadingState()
                }

                override fun onCancelled(error: DatabaseError) {
                    isRoleLoaded = true
                    updateLoadingState()
                }
            })
    }

    private fun configureUI() {
        assignSection.visibility = View.GONE
        statusSection.visibility = View.GONE

        when (userRole) {
            "Field Officer" -> statusSection.visibility = View.VISIBLE
            "Department Admin" -> {
                assignSection.visibility = View.VISIBLE
                loadOfficersByDepartment(userDepartment)
            }
            "Super Admin" -> {
                assignSection.visibility = View.VISIBLE
                loadOfficers()
            }
        }

        updateEtaActionVisibility()
    }

    private fun loadOfficers() {
        database.child("Users")
            .orderByChild("role")
            .equalTo("Field Officer")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    officerNames.clear()
                    officerIds.clear()

                    for (data in snapshot.children) {
                        val uid = data.key ?: continue
                        val name = data.child("name").value.toString()
                        val dept = data.child("department").value.toString()

                        officerNames.add("$name • $dept")
                        officerIds.add(uid)
                    }

                    bindOfficerSpinner()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadOfficersByDepartment(department: String) {
        database.child("Users")
            .orderByChild("department")
            .equalTo(department)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    officerNames.clear()
                    officerIds.clear()

                    for (data in snapshot.children) {
                        if (data.child("role").value.toString() != "Field Officer") continue

                        val uid = data.key ?: continue
                        val name = data.child("name").value.toString()
                        officerNames.add(name)
                        officerIds.add(uid)
                    }

                    bindOfficerSpinner()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun bindOfficerSpinner() {
        val displayItems = if (officerNames.isEmpty()) {
            listOf("No field officers available")
        } else {
            officerNames
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            displayItems
        )

        spOfficer.adapter = adapter
        spOfficer.isEnabled = officerIds.isNotEmpty()
        preselectAssignedOfficer(currentComplaint?.allottedOfficerId.orEmpty())
    }

    private fun preselectAssignedOfficer(assignedOfficerId: String) {
        if (assignedOfficerId.isBlank()) return

        val selectedIndex = officerIds.indexOf(assignedOfficerId)
        if (selectedIndex >= 0) {
            spOfficer.setSelection(selectedIndex)
        }
    }

    private fun loadAssignedOfficerName(officerId: String) {
        database.child("Users").child(officerId)
            .get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.child("name").value?.toString().orEmpty()
                val department = snapshot.child("department").value?.toString().orEmpty()

                tvAssignedOfficerValue.text = when {
                    name.isNotBlank() && department.isNotBlank() -> "$name • $department"
                    name.isNotBlank() -> name
                    else -> "Assigned officer"
                }
            }
            .addOnFailureListener {
                tvAssignedOfficerValue.text = "Assigned officer"
            }
    }

    private fun updateLoadingState() {
        showLoading(!(isComplaintLoaded && isRoleLoaded))
    }

    private fun showLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        contentScroll.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun priorityLabel(priority: Int): String {
        return when (priority) {
            2 -> "High"
            1 -> "Medium"
            else -> "Low"
        }
    }

    private fun priorityValueFromLabel(label: String): Int {
        return when (label) {
            "High" -> 2
            "Medium" -> 1
            else -> 0
        }
    }

    private fun statusLabel(status: Int): String {
        return when (status) {
            0 -> "Pending"
            1 -> "In Progress"
            2 -> "Resolved"
            else -> "Unknown"
        }
    }

    private fun maybeMarkComplaintAsRead(complaint: Complaint) {
        val isAdminViewer = userRole == "Super Admin" || userRole == "Department Admin"
        if (!isAdminViewer || complaint.readByAdmin) return

        val key = complaintKey ?: return

        database.child("Complaints")
            .child(key)
            .child("readByAdmin")
            .setValue(true)

        currentComplaint = complaint.copy(readByAdmin = true).also {
            it.firebaseKey = complaint.firebaseKey
        }
    }

    private fun refreshEtaForComplaint(
        complaintKey: String,
        baseComplaint: Complaint,
        onComplete: (Complaint, Boolean) -> Unit
    ) {
        database.child("Complaints")
            .get()
            .addOnSuccessListener { snapshot ->
                val openDepartmentLoad = snapshot.children
                    .mapNotNull { item ->
                        item.getValue(Complaint::class.java)?.apply {
                            firebaseKey = item.key.orEmpty()
                        }
                    }
                    .count {
                        it.status != 2 &&
                            ComplaintDataFormatter.resolvedDepartment(it) == ComplaintDataFormatter.resolvedDepartment(baseComplaint)
                    }
                    .coerceAtLeast(if (baseComplaint.status != 2) 1 else 0)

                val etaPayload = ComplaintEtaManager.calculateEta(baseComplaint, openDepartmentLoad)
                if (etaPayload == null) {
                    onComplete(baseComplaint, false)
                    return@addOnSuccessListener
                }

                val refreshedComplaint = baseComplaint.copy(
                    etaHours = etaPayload.etaHours,
                    estimatedResolutionAt = etaPayload.estimatedResolutionAt,
                    etaUpdatedAt = etaPayload.etaUpdatedAt,
                    etaReason = etaPayload.etaReason
                ).also {
                    it.firebaseKey = baseComplaint.firebaseKey
                }

                if (!ComplaintEtaManager.shouldPersist(baseComplaint, etaPayload)) {
                        currentComplaint = refreshedComplaint
                        tvEtaValue.text = ComplaintEtaManager.etaDisplayLabel(refreshedComplaint)
                        tvEtaReasonValue.text = ComplaintEtaManager.etaReasonLabel(refreshedComplaint)
                        tvEtaNotifyStatus.text = etaNotifyStatusLabel(refreshedComplaint)
                        updateEtaActionVisibility()
                        onComplete(refreshedComplaint, false)
                        return@addOnSuccessListener
                }

                database.child("Complaints")
                    .child(complaintKey)
                    .updateChildren(
                        mapOf(
                            "etaHours" to etaPayload.etaHours,
                            "estimatedResolutionAt" to etaPayload.estimatedResolutionAt,
                            "etaUpdatedAt" to etaPayload.etaUpdatedAt,
                            "etaReason" to etaPayload.etaReason
                        )
                    )
                    .addOnSuccessListener {
                        currentComplaint = refreshedComplaint
                        tvEtaValue.text = ComplaintEtaManager.etaDisplayLabel(refreshedComplaint)
                        tvEtaReasonValue.text = ComplaintEtaManager.etaReasonLabel(refreshedComplaint)
                        tvEtaNotifyStatus.text = etaNotifyStatusLabel(refreshedComplaint)
                        updateEtaActionVisibility()
                        onComplete(refreshedComplaint, true)
                    }
                    .addOnFailureListener {
                        onComplete(baseComplaint, false)
                    }
            }
            .addOnFailureListener {
                onComplete(baseComplaint, false)
            }
    }

    private fun updateEtaActionVisibility() {
        val complaint = currentComplaint
        val canSendEta = complaint != null &&
            complaint.status != 2 &&
            complaint.civilianId.isNotBlank() &&
            userRole != "Field Officer"

        btnNotifyEta.visibility = if (canSendEta) View.VISIBLE else View.GONE
    }

    private fun etaNotifyStatusLabel(complaint: Complaint): String {
        if (complaint.etaNotificationSentAt <= 0L) {
            return "Not sent yet"
        }

        return "Sent on ${
            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                .format(Date(complaint.etaNotificationSentAt))
        }"
    }

    private fun DataSnapshot.readString(vararg keys: String): String? {
        for (key in keys) {
            val value = child(key).value?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
                return value
            }
        }
        return null
    }

    private fun DataSnapshot.readLong(vararg keys: String): Long? {
        for (key in keys) {
            val rawValue = child(key).value ?: continue
            val parsed = when (rawValue) {
                is Long -> rawValue
                is Int -> rawValue.toLong()
                is Double -> rawValue.toLong()
                is String -> rawValue.trim().toLongOrNull()
                else -> null
            }
            if (parsed != null && parsed > 0L) {
                return parsed
            }
        }
        return null
    }

    private fun DataSnapshot.readDouble(vararg keys: String): Double? {
        for (key in keys) {
            val rawValue = child(key).value ?: continue
            val parsed = when (rawValue) {
                is Double -> rawValue
                is Long -> rawValue.toDouble()
                is Int -> rawValue.toDouble()
                is String -> rawValue.trim().toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed != 0.0) {
                return parsed
            }
        }
        return null
    }

    private fun DataSnapshot.readNestedString(parentKey: String, childKey: String): String? {
        val parent = child(parentKey)
        if (!parent.exists()) return null
        val value = parent.child(childKey).value?.toString()?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() && it.lowercase(Locale.getDefault()) != "null" }
    }

    private fun DataSnapshot.readNestedDouble(parentKey: String, childKey: String): Double? {
        val parent = child(parentKey)
        if (!parent.exists()) return null
        val rawValue = parent.child(childKey).value ?: return null
        val parsed = when (rawValue) {
            is Double -> rawValue
            is Long -> rawValue.toDouble()
            is Int -> rawValue.toDouble()
            is String -> rawValue.trim().toDoubleOrNull()
            else -> null
        }
        return parsed?.takeIf { it != 0.0 }
    }

    companion object {
        private const val ARG_COMPLAINT_KEY = "complaintKey"

        fun newInstance(complaintKey: String): ComplaintDetailFragment {
            val fragment = ComplaintDetailFragment()
            val bundle = Bundle()
            bundle.putString(ARG_COMPLAINT_KEY, complaintKey)
            fragment.arguments = bundle
            return fragment
        }
    }
}
