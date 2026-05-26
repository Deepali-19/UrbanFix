package com.example.urban.loginSingUp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.urban.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// This screen opens from the approval email and lets Urban Fix management review the request.
class EmailApprovalActivity : AppCompatActivity() {

    private lateinit var loadingView: View
    private lateinit var contentView: View
    private lateinit var statusView: TextView
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var roleView: TextView
    private lateinit var departmentView: TextView
    private lateinit var cityView: TextView
    private lateinit var submittedView: TextView
    private lateinit var viewIdButton: MaterialButton
    private lateinit var acceptButton: MaterialButton
    private lateinit var rejectButton: MaterialButton

    private val database = FirebaseDatabase.getInstance().reference
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    private var currentRequest: ApprovalRequest? = null
    private var requestId: String = ""
    private var actionToken: String = ""
    private var requestedAction: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_approval)

        requestId = intent?.data?.getQueryParameter("requestId").orEmpty()
        actionToken = intent?.data?.getQueryParameter("token").orEmpty()
        requestedAction = intent?.data?.getQueryParameter("action").orEmpty()

        bindViews()
        bindClicks()
        loadRequest()
    }

    // This connects the layout views once when the screen opens.
    private fun bindViews() {
        loadingView = findViewById(R.id.emailApprovalLoading)
        contentView = findViewById(R.id.emailApprovalContent)
        statusView = findViewById(R.id.tvEmailApprovalStatus)
        nameView = findViewById(R.id.tvEmailApprovalName)
        emailView = findViewById(R.id.tvEmailApprovalEmail)
        roleView = findViewById(R.id.tvEmailApprovalRole)
        departmentView = findViewById(R.id.tvEmailApprovalDepartment)
        cityView = findViewById(R.id.tvEmailApprovalCity)
        submittedView = findViewById(R.id.tvEmailApprovalSubmitted)
        viewIdButton = findViewById(R.id.btnEmailApprovalViewId)
        acceptButton = findViewById(R.id.btnEmailApprovalAccept)
        rejectButton = findViewById(R.id.btnEmailApprovalReject)
    }

    // This wires the main buttons for review actions.
    private fun bindClicks() {
        findViewById<ImageView>(R.id.btnEmailApprovalBack).setOnClickListener { finish() }

        viewIdButton.setOnClickListener {
            val imageUrl = currentRequest?.idProofUrl.orEmpty()
            if (imageUrl.isNotBlank()) {
                openIdPreview(imageUrl)
            }
        }

        acceptButton.setOnClickListener {
            currentRequest?.let(::approveRequest)
        }

        rejectButton.setOnClickListener {
            currentRequest?.let(::openRejectDialog)
        }
    }

    // This loads the pending request and validates the email token before showing it.
    private fun loadRequest() {
        if (requestId.isBlank() || actionToken.isBlank()) {
            showOnlyStatus(getString(R.string.email_approval_invalid_link))
            return
        }

        showLoading(true)
        database.child("ApprovalRequests").child(requestId)
            .get()
            .addOnSuccessListener { snapshot ->
                val request = snapshot.getValue(ApprovalRequest::class.java)
                if (request == null) {
                    showOnlyStatus(getString(R.string.email_approval_request_not_found))
                    return@addOnSuccessListener
                }

                if (request.actionToken != actionToken) {
                    showOnlyStatus(getString(R.string.email_approval_invalid_link))
                    return@addOnSuccessListener
                }

                if (request.status != AccountApprovalManager.STATUS_PENDING) {
                    showOnlyStatus(getString(R.string.email_approval_already_handled))
                    return@addOnSuccessListener
                }

                currentRequest = request
                showRequestDetails(request)

                if (requestedAction == "reject") {
                    rejectButton.post { openRejectDialog(request) }
                }
            }
            .addOnFailureListener {
                showOnlyStatus(getString(R.string.approval_action_failed))
            }
    }

    // This fills the request details after the email link is verified.
    private fun showRequestDetails(request: ApprovalRequest) {
        nameView.text = request.name.ifBlank { getString(R.string.common_no_data) }
        emailView.text = request.email.ifBlank { getString(R.string.common_no_data) }
        roleView.text = request.role.ifBlank { getString(R.string.common_no_data) }
        departmentView.text = request.department.ifBlank { getString(R.string.common_no_data) }
        cityView.text = request.city.ifBlank { getString(R.string.common_no_data) }
        submittedView.text = dateFormat.format(Date(request.submittedAt))
        viewIdButton.visibility = if (request.idProofUrl.isBlank()) View.GONE else View.VISIBLE

        statusView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        showLoading(false)
    }

    // This runs the same approval save flow used by the app approval system.
    private fun approveRequest(request: ApprovalRequest) {
        setActionButtonsEnabled(false)
        showLoading(true)

        EmailApprovalProcessor.approveRequest(
            request = request,
            onSuccess = { employeeId ->
                showOnlyStatus(getString(R.string.email_approval_approved_message, employeeId))
                Toast.makeText(this, getString(R.string.approval_accept_success), Toast.LENGTH_SHORT).show()
            },
            onFailure = { message ->
                showLoading(false)
                setActionButtonsEnabled(true)
                toast(message)
            }
        )
    }

    // This asks for a reject reason before the request is rejected.
    private fun openRejectDialog(request: ApprovalRequest) {
        val input = EditText(this)
        input.hint = getString(R.string.approval_reject_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.approval_reject_title)
            .setView(input)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.approval_reject, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reason = input.text.toString().trim()
                        if (reason.isBlank()) {
                            input.error = getString(R.string.approval_reject_required)
                            return@setOnClickListener
                        }

                        dismiss()
                        rejectRequest(request, reason)
                    }
                }
            }
            .show()
    }

    // This saves the rejected status and then shows the final result screen.
    private fun rejectRequest(request: ApprovalRequest, reason: String) {
        setActionButtonsEnabled(false)
        showLoading(true)

        EmailApprovalProcessor.rejectRequest(
            request = request,
            reason = reason,
            onSuccess = {
                showOnlyStatus(getString(R.string.email_approval_rejected_message))
                Toast.makeText(this, getString(R.string.approval_reject_success), Toast.LENGTH_SHORT).show()
            },
            onFailure = { message ->
                showLoading(false)
                setActionButtonsEnabled(true)
                toast(message)
            }
        )
    }

    // This opens the uploaded ID proof in the same full-screen image preview style.
    private fun openIdPreview(imageUrl: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_complaint_image_preview)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val imageView = dialog.findViewById<ImageView>(R.id.imgComplaintPreview)
        val closeButton = dialog.findViewById<ImageView>(R.id.btnClosePreview)

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .into(imageView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // This swaps the screen into a simple final status state after the request is handled.
    private fun showOnlyStatus(message: String) {
        currentRequest = null
        showLoading(false)
        contentView.visibility = View.GONE
        statusView.visibility = View.VISIBLE
        statusView.text = message
    }

    // This keeps the loading layer visible only while work is happening.
    private fun showLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // This prevents duplicate taps while the request is being saved.
    private fun setActionButtonsEnabled(enabled: Boolean) {
        acceptButton.isEnabled = enabled
        rejectButton.isEnabled = enabled
        viewIdButton.isEnabled = enabled
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
