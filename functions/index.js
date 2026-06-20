const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

const STATUS_PENDING = "pending";
const STATUS_APPROVED = "approved";
const STATUS_REJECTED = "rejected";
const ROOT_EMAIL_APPROVER = "root_email";

const EMAIL_STATUS_QUEUED = "queued";
const EMAIL_STATUS_SENDING = "sending";
const EMAIL_STATUS_SENT = "sent";
const EMAIL_STATUS_FAILED = "failed";
const EMAIL_STATUS_SKIPPED = "skipped";

const MAX_RESEND_BATCH = 50;

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

exports.processApprovalEmailQueue = functions.database.ref("/ApprovalEmailQueue/{requestId}").onWrite(async (change, context) => {
  const after = change.after.val();
  if (!after) return null;

  const before = change.before.exists() ? change.before.val() || {} : null;
  const afterStatus = normalizeEmailStatus(after.emailStatus);
  const beforeStatus = normalizeEmailStatus(before?.emailStatus);
  const resendMarkerChanged = Number(after.resendRequestedAt || 0) !== Number(before?.resendRequestedAt || 0);

  const shouldSend = afterStatus === EMAIL_STATUS_QUEUED && (
    !change.before.exists() ||
    beforeStatus !== EMAIL_STATUS_QUEUED ||
    resendMarkerChanged
  );

  if (!shouldSend) return null;

  await processQueuedApprovalEmail(context.params.requestId, after);
  return null;
});

exports.resendPendingApprovalEmails = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Use POST to resend pending approval emails." });
    return;
  }

  if (!isResendRequestAuthorized(req)) {
    res.status(403).json({ error: "Unauthorized resend request." });
    return;
  }

  try {
    const limit = clampBatchLimit(readNumber(req.body?.limit || req.query?.limit));
    const snapshot = await admin.database()
      .ref("ApprovalRequests")
      .orderByChild("status")
      .equalTo(STATUS_PENDING)
      .get();

    if (!snapshot.exists()) {
      res.status(200).json({
        queuedCount: 0,
        skippedCount: 0,
        message: "No pending approval requests were found."
      });
      return;
    }

    let queuedCount = 0;
    let skippedCount = 0;
    const queuedRequestIds = [];

    for (const child of snapshot.children) {
      if (queuedCount >= limit) break;

      const requestId = child.key || "";
      const requestData = child.val() || {};
      if (!requestId || !String(requestData.targetApproverEmail || "").trim()) {
        skippedCount += 1;
        continue;
      }

      await queueApprovalEmail(requestId, requestData, { resend: true });
      queuedCount += 1;
      queuedRequestIds.push(requestId);
    }

    res.status(200).json({
      queuedCount,
      skippedCount,
      requestIds: queuedRequestIds,
      message: queuedCount > 0
        ? "Pending approval emails have been queued for resend."
        : "No resendable approval emails were found."
    });
  } catch (error) {
    console.error("resendPendingApprovalEmails failed", error);
    res.status(500).json({
      error: "Unable to queue pending approval emails right now."
    });
  }
});

function readValue(req, key) {
  if (req.method === "POST") {
    return String(req.body?.[key] || "").trim();
  }
  return String(req.query?.[key] || "").trim();
}

function readNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function clampBatchLimit(value) {
  if (!value || value < 1) return MAX_RESEND_BATCH;
  return Math.min(Math.floor(value), MAX_RESEND_BATCH);
}

function normalizeEmailStatus(value) {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized || EMAIL_STATUS_QUEUED;
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

async function processQueuedApprovalEmail(requestId, queueData) {
  const queueRef = admin.database().ref(`ApprovalEmailQueue/${requestId}`);
  const attemptCount = Number(queueData.attemptCount || 0) + 1;
  const attemptStartedAt = Date.now();

  await queueRef.update({
    emailStatus: EMAIL_STATUS_SENDING,
    attemptCount,
    lastAttemptAt: attemptStartedAt,
    lastError: ""
  });

  try {
    const requestSnapshot = await admin.database().ref(`ApprovalRequests/${requestId}`).get();
    const requestData = requestSnapshot.exists() ? requestSnapshot.val() || {} : {};

    if (String(requestData.status || STATUS_PENDING).toLowerCase() !== STATUS_PENDING) {
      await queueRef.update({
        emailStatus: EMAIL_STATUS_SKIPPED,
        skippedAt: Date.now(),
        lastError: "Approval request is no longer pending."
      });
      return;
    }

    const emailRequest = buildApprovalEmailRequest(requestId, requestData, queueData);
    if (!emailRequest.targetApproverEmail) {
      await queueRef.update({
        emailStatus: EMAIL_STATUS_FAILED,
        failedAt: Date.now(),
        lastError: "Missing approval target email."
      });
      return;
    }

    const info = await sendApprovalRequestEmail(emailRequest);
    await queueRef.update({
      emailStatus: EMAIL_STATUS_SENT,
      sentAt: Date.now(),
      transportMessageId: info.messageId || "",
      lastError: ""
    });
  } catch (error) {
    console.error(`processQueuedApprovalEmail failed for ${requestId}`, error);
    await queueRef.update({
      emailStatus: EMAIL_STATUS_FAILED,
      failedAt: Date.now(),
      lastError: cleanErrorMessage(error)
    });
  }
}

async function queueApprovalEmail(requestId, requestData, options = {}) {
  const queuedAt = Date.now();
  const payload = {
    requestId,
    toEmail: String(requestData.targetApproverEmail || "").trim(),
    role: String(requestData.role || "").trim(),
    name: String(requestData.name || "").trim(),
    requesterEmail: String(requestData.email || "").trim(),
    department: String(requestData.department || "").trim(),
    city: String(requestData.city || "").trim(),
    idProofUrl: String(requestData.idProofUrl || "").trim(),
    actionToken: String(requestData.actionToken || "").trim(),
    submittedAt: Number(requestData.submittedAt || 0),
    approvalRoute: String(requestData.approvalRoute || "").trim(),
    emailStatus: EMAIL_STATUS_QUEUED,
    lastQueuedAt: queuedAt,
    lastError: ""
  };

  if (options.resend) {
    payload.resendRequestedAt = queuedAt;
  }

  await admin.database().ref(`ApprovalEmailQueue/${requestId}`).update(payload);
}

async function sendApprovalRequestEmail(request) {
  const config = getApprovalEmailConfig();
  if (!config.smtpEmail) {
    throw new Error("Missing SMTP email configuration.");
  }
  if (!config.smtpAppPassword) {
    throw new Error("Missing SMTP app password configuration.");
  }
  if (!request.targetApproverEmail) {
    throw new Error("Missing approval target email.");
  }

  const transporter = nodemailer.createTransport({
    host: config.smtpHost,
    port: config.smtpPort,
    secure: config.smtpSecure,
    auth: {
      user: config.smtpEmail,
      pass: config.smtpAppPassword
    }
  });

  return transporter.sendMail({
    from: `"Urban Fix" <${config.smtpEmail}>`,
    to: request.targetApproverEmail,
    subject: `Urban Fix Approval Request - ${request.role} - ${request.city}`,
    text: buildPlainTextBody(request, config.approvalActionBaseUrl),
    html: buildHtmlBody(request, config.approvalActionBaseUrl)
  });
}

function buildApprovalEmailRequest(requestId, requestData, queueData) {
  return {
    requestId,
    uid: String(requestData.uid || queueData.requestId || requestId),
    name: String(requestData.name || queueData.name || "").trim(),
    email: String(requestData.email || queueData.requesterEmail || "").trim(),
    role: String(requestData.role || queueData.role || "").trim(),
    department: String(requestData.department || queueData.department || "").trim(),
    city: String(requestData.city || queueData.city || "").trim(),
    idProofUrl: String(requestData.idProofUrl || queueData.idProofUrl || "").trim(),
    approvalRoute: String(requestData.approvalRoute || queueData.approvalRoute || "").trim(),
    targetApproverEmail: String(requestData.targetApproverEmail || queueData.toEmail || "").trim(),
    actionToken: String(requestData.actionToken || queueData.actionToken || "").trim(),
    submittedAt: Number(requestData.submittedAt || queueData.submittedAt || 0)
  };
}

function getApprovalEmailConfig() {
  const runtimeConfig = safeRuntimeConfig();
  const approvalConfig = runtimeConfig.approval || {};
  const mailConfig = runtimeConfig.mail || {};

  return {
    smtpEmail: readConfiguredValue(
      process.env.SMTP_EMAIL,
      process.env.APPROVAL_SMTP_EMAIL,
      approvalConfig.smtp_email,
      mailConfig.email
    ),
    smtpAppPassword: readConfiguredValue(
      process.env.SMTP_APP_PASSWORD,
      process.env.APPROVAL_SMTP_APP_PASSWORD,
      approvalConfig.smtp_app_password,
      mailConfig.app_password
    ),
    approvalActionBaseUrl: readConfiguredValue(
      process.env.APPROVAL_ACTION_BASE_URL,
      approvalConfig.action_base_url
    ),
    smtpHost: readConfiguredValue(
      process.env.SMTP_HOST,
      approvalConfig.smtp_host,
      "smtp.gmail.com"
    ),
    smtpPort: Number(readConfiguredValue(
      process.env.SMTP_PORT,
      approvalConfig.smtp_port,
      "587"
    )),
    smtpSecure: readConfiguredValue(
      process.env.SMTP_SECURE,
      approvalConfig.smtp_secure,
      "false"
    ) === "true",
    adminKey: readConfiguredValue(
      process.env.APPROVAL_EMAIL_ADMIN_KEY,
      approvalConfig.admin_key
    )
  };
}

function safeRuntimeConfig() {
  try {
    return typeof functions.config === "function" ? functions.config() || {} : {};
  } catch (error) {
    console.warn("Unable to read functions config.", error);
    return {};
  }
}

function readConfiguredValue(...values) {
  for (const value of values) {
    const normalized = String(value || "").trim();
    if (normalized) return normalized;
  }
  return "";
}

function isResendRequestAuthorized(req) {
  const config = getApprovalEmailConfig();
  const expectedKey = config.adminKey;
  if (!expectedKey) {
    console.warn("resendPendingApprovalEmails is running without APPROVAL_EMAIL_ADMIN_KEY protection.");
    return true;
  }

  const providedKey = readConfiguredValue(
    req.headers["x-approval-email-admin-key"],
    req.body?.key,
    req.query?.key
  );

  return providedKey === expectedKey;
}

function buildPlainTextBody(request, baseUrl) {
  const actions = buildActionLinks(request, baseUrl);

  return [
    "Urban Fix approval request",
    "",
    "A new account request has been submitted.",
    "",
    `Name: ${request.name}`,
    `Email: ${request.email}`,
    `Role: ${request.role}`,
    `Department: ${request.department}`,
    `City: ${request.city}`,
    `Submitted at: ${formatTimestamp(request.submittedAt)}`,
    `ID Proof: ${request.idProofUrl}`,
    "",
    `Request ID: ${request.requestId}`,
    `Approval route: ${request.approvalRoute}`,
    "",
    ...(actions.length > 0
      ? actions.map(({ label, url }) => `${label}: ${url}`)
      : ["Approval action URL is not configured yet."])
  ].join("\n");
}

function buildHtmlBody(request, baseUrl) {
  const actions = buildActionLinks(request, baseUrl);
  const actionButtonsHtml = actions.length === 0
    ? `
      <p style="margin:24px 0 0;color:#6b7280;font-size:14px;">
        Approval action URL is not configured yet. Please deploy the approval action endpoint and set
        <b>APPROVAL_ACTION_BASE_URL</b>.
      </p>
    `
    : `
      <div style="margin:24px 0 0;">
        ${actions.map((action, index) => renderActionButton(action, index)).join("")}
      </div>
    `;

  return `
    <html>
      <body style="margin:0;padding:24px;background:#F4F8FD;font-family:Arial,sans-serif;color:#1F2937;">
        <div style="max-width:680px;margin:0 auto;background:#FFFFFF;border-radius:20px;padding:28px;box-shadow:0 8px 30px rgba(17,24,39,0.08);">
          <p style="margin:0 0 20px;font-size:14px;color:#4B5563;">Urban Fix approval request</p>
          <h2 style="margin:0 0 12px;font-size:24px;color:#0F172A;">A new account request has been submitted.</h2>
          <p style="margin:0 0 24px;font-size:15px;color:#475569;">
            Review the request details below and choose an approval action.
          </p>
          <table style="width:100%;border-collapse:collapse;font-size:15px;">
            ${tableRow("Name", request.name)}
            ${tableRow("Email", request.email)}
            ${tableRow("Role", request.role)}
            ${tableRow("Department", request.department)}
            ${tableRow("City", request.city)}
            ${tableRow("Submitted at", formatTimestamp(request.submittedAt))}
            ${tableRow("Request ID", request.requestId)}
            ${tableRow("Approval route", request.approvalRoute)}
          </table>
          <div style="margin:20px 0 0;">
            <p style="margin:0 0 8px;font-size:13px;font-weight:600;color:#64748B;">ID Proof</p>
            <a href="${escapeHtml(request.idProofUrl)}" style="color:#1665D8;word-break:break-all;">${escapeHtml(request.idProofUrl)}</a>
          </div>
          ${actionButtonsHtml}
        </div>
      </body>
    </html>
  `;
}

function renderActionButton(action, index) {
  const background = action.label === "Accept" ? "#1665D8" : "#E8EEF7";
  const color = action.label === "Accept" ? "#FFFFFF" : "#1F2937";
  const spacer = index > 0 ? `<span style="display:inline-block;width:12px;"></span>` : "";

  return `
    ${spacer}
    <a href="${action.url}"
       style="display:inline-block;padding:12px 20px;border-radius:999px;background:${background};color:${color};
              text-decoration:none;font-weight:600;font-size:14px;border:1px solid #D7E3F4;">
      ${escapeHtml(action.label)}
    </a>
  `;
}

function buildActionLinks(request, baseUrl) {
  const trimmedBaseUrl = String(baseUrl || "").trim().replace(/\/+$/, "");
  if (!trimmedBaseUrl || !request.actionToken) return [];

  return [
    { label: "Accept", url: buildActionUrl(trimmedBaseUrl, request, "approve") },
    { label: "Reject", url: buildActionUrl(trimmedBaseUrl, request, "reject") }
  ];
}

function buildActionUrl(baseUrl, request, action) {
  const separator = baseUrl.includes("?") ? "&" : "?";
  const params = new URLSearchParams({
    requestId: request.requestId,
    token: request.actionToken,
    action
  });

  return `${baseUrl}${separator}${params.toString()}`;
}

function formatTimestamp(value) {
  const timestamp = Number(value || 0);
  if (!timestamp) return "Not available";

  try {
    return new Date(timestamp).toLocaleString("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "Asia/Kolkata"
    });
  } catch (error) {
    return String(timestamp);
  }
}

function tableRow(label, value) {
  return `
    <tr>
      <td style="padding:10px 0 6px;font-size:13px;font-weight:600;color:#64748B;width:160px;vertical-align:top;">${escapeHtml(label)}</td>
      <td style="padding:10px 0 6px;font-size:15px;color:#111827;vertical-align:top;">${escapeHtml(value)}</td>
    </tr>
  `;
}

function cleanErrorMessage(error) {
  const message = error?.message || String(error || "Unknown error");
  return message.slice(0, 400);
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
