const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();

const STATUS_PENDING = "pending";
const STATUS_APPROVED = "approved";
const STATUS_REJECTED = "rejected";
const ROOT_EMAIL_APPROVER = "root_email";

exports.approvalAction = functions.https.onRequest(async (req, res) => {
  const requestId = readValue(req, "requestId");
  const action = readValue(req, "action").toLowerCase();
  const token = readValue(req, "token");
  const reason = readValue(req, "reason");

  if (!requestId || !action || !token) {
    res.status(400).send(renderMessagePage("Missing details", "Request ID, action, and token are required."));
    return;
  }

  try {
    const requestSnapshot = await admin.database().ref(`ApprovalRequests/${requestId}`).get();
    if (!requestSnapshot.exists()) {
      res.status(404).send(renderMessagePage("Request not found", "This approval request does not exist anymore."));
      return;
    }

    const requestData = requestSnapshot.val() || {};
    if ((requestData.actionToken || "") !== token) {
      res.status(403).send(renderMessagePage("Invalid link", "This approval link is not valid."));
      return;
    }

    if ((requestData.status || "").toLowerCase() !== STATUS_PENDING) {
      res.status(200).send(renderMessagePage("Already handled", "This request has already been approved or rejected."));
      return;
    }

    if (action === "reject" && !reason) {
      res.status(200).send(renderRejectForm(requestId, token, requestData));
      return;
    }

    if (action === "approve") {
      const employeeId = await reserveEmployeeId(requestData.city || "", requestData.role || "");
      await completeApproval(requestData, requestId, employeeId);
      res.status(200).send(renderMessagePage("Request approved", `Employee ID ${employeeId} has been assigned and the account is now approved.`));
      return;
    }

    if (action === "reject") {
      await rejectRequest(requestData, requestId, reason);
      res.status(200).send(renderMessagePage("Request rejected", "The account request has been rejected successfully."));
      return;
    }

    res.status(400).send(renderMessagePage("Unknown action", "This approval action is not supported."));
  } catch (error) {
    console.error("approvalAction failed", error);
    res.status(500).send(renderMessagePage("Something went wrong", "The approval action could not be completed right now."));
  }
});

function readValue(req, key) {
  if (req.method === "POST") {
    return String(req.body?.[key] || "").trim();
  }
  return String(req.query?.[key] || "").trim();
}

async function reserveEmployeeId(city, role) {
  const cityCode = buildCityCode(city);
  const roleCode = buildRoleCode(role);
  const counterRef = admin.database().ref(`EmployeeIdCounters/${cityCode}/${roleCode}`);
  const result = await counterRef.transaction((currentValue) => {
    const current = Number.isInteger(currentValue) ? currentValue : 0;
    return current + 1;
  });

  const runningNumber = result.snapshot?.val() || 1;
  return `${cityCode}-${roleCode}-${String(runningNumber).padStart(3, "0")}`;
}

async function completeApproval(requestData, requestId, employeeId) {
  const handledAt = Date.now();
  const updates = {
    [`/Users/${requestData.uid}/employeeId`]: employeeId,
    [`/Users/${requestData.uid}/accountStatus`]: STATUS_APPROVED,
    [`/Users/${requestData.uid}/approvedAt`]: handledAt,
    [`/Users/${requestData.uid}/approvedBy`]: ROOT_EMAIL_APPROVER,
    [`/Users/${requestData.uid}/rejectedAt`]: 0,
    [`/Users/${requestData.uid}/rejectedBy`]: "",
    [`/Users/${requestData.uid}/rejectionReason`]: "",
    [`/ApprovalRequests/${requestId}/status`]: STATUS_APPROVED,
    [`/ApprovalRequests/${requestId}/handledAt`]: handledAt,
    [`/ApprovalRequests/${requestId}/handledBy`]: ROOT_EMAIL_APPROVER,
    [`/ApprovalRequests/${requestId}/rejectionReason`]: "",
    [`/ApprovalRequests/${requestId}/actionToken`]: "",
    [`/ApprovalEmailQueue/${requestId}`]: null,
    [`/AccountNotifications/${requestData.uid}/${requestId}_approved`]: {
      title: "Account Approved",
      body: "Your Urban Fix account has been approved. You can now log in.",
      type: "account_approved",
      timestamp: handledAt
    }
  };

  await admin.database().ref().update(updates);
  await sendPushIfPossible(
    requestData.requesterDeviceToken,
    "Account Approved",
    "Your Urban Fix account has been approved. You can now log in.",
    "account_approved"
  );
}

async function rejectRequest(requestData, requestId, reason) {
  const handledAt = Date.now();
  const safeReason = reason || "Rejected by Urban Fix management";
  const updates = {
    [`/Users/${requestData.uid}/accountStatus`]: STATUS_REJECTED,
    [`/Users/${requestData.uid}/rejectedAt`]: handledAt,
    [`/Users/${requestData.uid}/rejectedBy`]: ROOT_EMAIL_APPROVER,
    [`/Users/${requestData.uid}/rejectionReason`]: safeReason,
    [`/ApprovalRequests/${requestId}/status`]: STATUS_REJECTED,
    [`/ApprovalRequests/${requestId}/handledAt`]: handledAt,
    [`/ApprovalRequests/${requestId}/handledBy`]: ROOT_EMAIL_APPROVER,
    [`/ApprovalRequests/${requestId}/rejectionReason`]: safeReason,
    [`/ApprovalRequests/${requestId}/actionToken`]: "",
    [`/ApprovalEmailQueue/${requestId}`]: null,
    [`/AccountNotifications/${requestData.uid}/${requestId}_rejected`]: {
      title: "Account Rejected",
      body: "Your Urban Fix account request has been rejected.",
      type: "account_rejected",
      timestamp: handledAt,
      reason: safeReason
    }
  };

  await admin.database().ref().update(updates);
  await sendPushIfPossible(
    requestData.requesterDeviceToken,
    "Account Rejected",
    `Your Urban Fix account request has been rejected. Reason: ${safeReason}`,
    "account_rejected"
  );
}

async function sendPushIfPossible(deviceToken, title, body, type) {
  if (!deviceToken) return;

  try {
    await admin.messaging().send({
      token: deviceToken,
      notification: {
        title,
        body
      },
      data: {
        title,
        body,
        type,
        timestamp: String(Date.now())
      },
      android: {
        priority: "high"
      }
    });
  } catch (error) {
    console.error("sendPushIfPossible failed", error);
  }
}

function buildRoleCode(role) {
  switch (String(role || "").trim()) {
    case "Super Admin":
      return "SA";
    case "Department Admin":
      return "DA";
    case "Field Officer":
      return "FO";
    default:
      return "OT";
  }
}

function buildCityCode(city) {
  const cleaned = String(city || "")
    .toUpperCase()
    .replace(/[^A-Z]/g, "");

  if (!cleaned) return "CTY";
  if (cleaned.length >= 3) return cleaned.slice(0, 3);
  return cleaned.padEnd(3, "X");
}

function renderRejectForm(requestId, token, requestData) {
  return `
    <html>
      <body style="margin:0;padding:24px;background:#F4F8FD;font-family:Arial,sans-serif;color:#1F2937;">
        <div style="max-width:640px;margin:0 auto;background:#FFFFFF;border-radius:20px;padding:28px;box-shadow:0 8px 30px rgba(17,24,39,0.08);">
          <p style="margin:0 0 8px;color:#64748B;font-size:14px;">Urban Fix approval</p>
          <h2 style="margin:0 0 16px;">Reject ${escapeHtml(requestData.role || "account")} request</h2>
          <p style="margin:0 0 20px;color:#475569;">Add a reason before rejecting this request.</p>
          <form method="GET">
            <input type="hidden" name="requestId" value="${escapeHtml(requestId)}" />
            <input type="hidden" name="token" value="${escapeHtml(token)}" />
            <input type="hidden" name="action" value="reject" />
            <label style="display:block;font-size:14px;font-weight:600;color:#475569;margin-bottom:8px;">Rejection reason</label>
            <textarea
              name="reason"
              rows="4"
              required
              style="width:100%;padding:14px;border:1px solid #D7E3F4;border-radius:14px;font-size:14px;resize:vertical;box-sizing:border-box;"
            ></textarea>
            <button
              type="submit"
              style="margin-top:18px;padding:12px 22px;border:none;border-radius:999px;background:#1665D8;color:#FFFFFF;font-weight:600;font-size:14px;cursor:pointer;"
            >
              Reject Request
            </button>
          </form>
        </div>
      </body>
    </html>
  `;
}

function renderMessagePage(title, body) {
  return `
    <html>
      <body style="margin:0;padding:24px;background:#F4F8FD;font-family:Arial,sans-serif;color:#1F2937;">
        <div style="max-width:640px;margin:0 auto;background:#FFFFFF;border-radius:20px;padding:28px;box-shadow:0 8px 30px rgba(17,24,39,0.08);">
          <p style="margin:0 0 8px;color:#64748B;font-size:14px;">Urban Fix approval</p>
          <h2 style="margin:0 0 16px;">${escapeHtml(title)}</h2>
          <p style="margin:0;color:#475569;font-size:15px;line-height:1.6;">${escapeHtml(body)}</p>
        </div>
      </body>
    </html>
  `;
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
