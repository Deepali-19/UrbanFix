package com.example.urban.bottomNavigation.complaint

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.urban.R
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ComplaintDetailFragment : Fragment(R.layout.fragment_complaint_detail) {

    private lateinit var database: DatabaseReference

    private lateinit var tvTitle: TextView
    private lateinit var tvId: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDescription: TextView
    private lateinit var imgComplaint: ImageView

    private lateinit var spOfficer: Spinner
    private lateinit var statusGroup: RadioGroup
    private lateinit var etRemark: EditText
    private lateinit var btnUpdate: Button

    private var complaintId: String? = null

    private val officerNames = ArrayList<String>()
    private val officerIds = ArrayList<String>()

    private var userRole = ""
    private var userDepartment = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        database = FirebaseDatabase.getInstance().reference

        tvTitle = view.findViewById(R.id.tvComplaintTitle)
        tvId = view.findViewById(R.id.tvComplaintId)
        tvLocation = view.findViewById(R.id.tvLocation)
        tvDate = view.findViewById(R.id.tvDate)
        tvDescription = view.findViewById(R.id.tvDescription)
        imgComplaint = view.findViewById(R.id.imgComplaint)

        spOfficer = view.findViewById(R.id.spOfficer)
        statusGroup = view.findViewById(R.id.statusGroup)
        etRemark = view.findViewById(R.id.etRemark)
        btnUpdate = view.findViewById(R.id.btnUpdate)

        complaintId = arguments?.getString("complaintId")

        loadComplaint()
        loadUserRole()

        btnUpdate.setOnClickListener {
            updateComplaint()
        }
    }

    private fun loadComplaint() {

        tvTitle.text = "Loading..."
        tvDescription.text =""

        database.child("Complaints")
            .child(complaintId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val complaint = snapshot.getValue(Complaint::class.java) ?: return

                    tvTitle.text = complaint.title
                    tvId.text = "Complaint ID: ${complaint.complaintId}"
                    tvLocation.text = complaint.location

                    tvDescription.text =
                        """${complaint.description}
                        Issue Type: ${complaint.issueType}
                        Department: ${complaint.departmentId}
                        Location:${complaint.location}
                        """.trimIndent()

                    val date = Date(complaint.timestamp)
                    val format =
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    tvDate.text = format.format(date)

                    if (complaint.images.isNotEmpty()) {
                        Glide.with(requireContext())
                            .load(complaint.images[0])
                            .placeholder(R.drawable.ic_image)
                            .into(imgComplaint)
                    }

                    when (complaint.status) {
                        0 -> statusGroup.check(R.id.rbPending)
                        1 -> statusGroup.check(R.id.rbProgress)
                        2 -> statusGroup.check(R.id.rbResolved)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateComplaint() {

        val remark = etRemark.text.toString()

        val status = when (statusGroup.checkedRadioButtonId) {
            R.id.rbPending -> 0
            R.id.rbProgress -> 1
            R.id.rbResolved -> 2
            else -> 0
        }

        val update = HashMap<String, Any>()

        if (userRole == "Field Officer") {

            update["status"] = status
            update["feedback"] = remark

            if (status == 2) sendSMS()

        } else {
            // ✅ Admin → can assign officer ONLY
            val officerPosition = spOfficer.selectedItemPosition

            // safety check (important)
            if (officerPosition >= 0 && officerPosition < officerIds.size) {
                val officerId = officerIds[officerPosition]
                update["allottedOfficerId"] = officerId
            }

//            val officerPosition = spOfficer.selectedItemPosition
//            val officerId = officerIds[officerPosition]
//
//            update["allottedOfficerId"] = officerId
        }

        database.child("Complaints")
            .child(complaintId!!)
            .updateChildren(update)

        Toast.makeText(requireContext(), "Complaint Updated", Toast.LENGTH_SHORT).show()
    }

    private fun sendSMS() {

        val phone = "9460257394" // later take from DB

        val message = "Your complaint (${complaintId}) has been resolved."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", message)
        }

        startActivity(intent)
    }


    private fun loadUserRole() {

        val uid =
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child("Users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    userRole = snapshot.child("role").value.toString()
                    userDepartment = snapshot.child("department").value.toString()

                    configureUI()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun configureUI() {

        when (userRole) {

            "Field Officer" -> {
                spOfficer.visibility = View.GONE
            }

            "Department Admin" -> {
                loadOfficersByDepartment(userDepartment)
            }

            "Super Admin" -> {
                loadOfficers()
            }
        }
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

                        officerNames.add("$name : $dept")
                        officerIds.add(uid)
                    }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        officerNames
                    )

                    spOfficer.adapter = adapter
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

                        if (data.child("role").value.toString() != "Field Officer")
                            continue

                        val uid = data.key ?: continue
                        val name = data.child("name").value.toString()

                        officerNames.add(name)
                        officerIds.add(uid)
                    }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        officerNames
                    )

                    spOfficer.adapter = adapter
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    companion object {

        fun newInstance(id: String): ComplaintDetailFragment {

            val fragment = ComplaintDetailFragment()

            val bundle = Bundle()
            bundle.putString("complaintId", id)

            fragment.arguments = bundle

            return fragment
        }
    }
}