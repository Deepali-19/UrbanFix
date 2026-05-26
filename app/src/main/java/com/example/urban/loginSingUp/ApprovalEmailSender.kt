package com.example.urban.loginSingUp

import com.example.urban.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart

// This sends approval request emails through Gmail SMTP when sender credentials are configured.
object ApprovalEmailSender {

    fun isConfigured(): Boolean {
        return AppConfig.smtpEmail.isNotBlank() && AppConfig.smtpAppPassword.isNotBlank()
    }

    suspend fun sendApprovalRequestEmail(request: ApprovalRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val senderEmail = AppConfig.smtpEmail
            val senderPassword = AppConfig.smtpAppPassword

            require(senderEmail.isNotBlank()) { "Missing SMTP sender email" }
            require(senderPassword.isNotBlank()) { "Missing SMTP app password" }
            require(request.targetApproverEmail.isNotBlank()) { "Missing approval target email" }

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail, "Urban Fix"))
                setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(request.targetApproverEmail)
                )
                subject = "Urban Fix Approval Request - ${request.role} - ${request.city}"
                setContent(buildEmailContent(request))
            }

            Transport.send(message)
        }
    }

    private fun buildEmailContent(request: ApprovalRequest): Multipart {
        return MimeMultipart("alternative").apply {
            addBodyPart(
                MimeBodyPart().apply {
                    setText(buildPlainTextBody(request))
                }
            )
            addBodyPart(
                MimeBodyPart().apply {
                    setContent(buildHtmlBody(request), "text/html; charset=utf-8")
                }
            )
        }
    }

    private fun buildPlainTextBody(request: ApprovalRequest): String {
        return buildString {
            appendLine("Urban Fix approval request")
            appendLine()
            appendLine("A new account request has been submitted.")
            appendLine()
            appendLine("Name: ${request.name}")
            appendLine("Email: ${request.email}")
            appendLine("Role: ${request.role}")
            appendLine("Department: ${request.department}")
            appendLine("City: ${request.city}")
            appendLine("Submitted at: ${request.submittedAt}")
            appendLine("ID Proof: ${request.idProofUrl}")
            appendLine()
            appendLine("Request ID: ${request.requestId}")
            appendLine("Approval route: ${request.approvalRoute}")
            appendLine()
            buildActionLinks(request).forEach { (label, url) ->
                appendLine("$label: $url")
            }
            if (buildActionLinks(request).isEmpty()) {
                appendLine("Approval action URL is not configured yet.")
            }
        }
    }

    private fun buildHtmlBody(request: ApprovalRequest): String {
        val actions = buildActionLinks(request)
        val actionButtonsHtml = if (actions.isEmpty()) {
            """
            <p style="margin:24px 0 0;color:#6b7280;font-size:14px;">
              Approval action URL is not configured yet. Please deploy the approval action endpoint and set
              <b>APPROVAL_ACTION_BASE_URL</b>.
            </p>
            """.trimIndent()
        } else {
            buildString {
                append("""<div style="margin:24px 0 0;">""")
                actions.forEachIndexed { index, pair ->
                    val (label, url) = pair
                    val background = if (label == "Accept") "#1665D8" else "#E8EEF7"
                    val color = if (label == "Accept") "#FFFFFF" else "#1F2937"
                    if (index > 0) append("""<span style="display:inline-block;width:12px;"></span>""")
                    append(
                        """
                        <a href="$url"
                           style="display:inline-block;padding:12px 20px;border-radius:999px;background:$background;color:$color;
                                  text-decoration:none;font-weight:600;font-size:14px;border:1px solid #D7E3F4;">
                          $label
                        </a>
                        """.trimIndent()
                    )
                }
                append("</div>")
            }
        }

        return """
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
                    ${tableRow("Submitted at", request.submittedAt.toString())}
                    ${tableRow("Request ID", request.requestId)}
                    ${tableRow("Approval route", request.approvalRoute)}
                  </table>
                  <div style="margin:20px 0 0;">
                    <p style="margin:0 0 8px;font-size:13px;font-weight:600;color:#64748B;">ID Proof</p>
                    <a href="${escapeHtml(request.idProofUrl)}" style="color:#1665D8;word-break:break-all;">${escapeHtml(request.idProofUrl)}</a>
                  </div>
                  $actionButtonsHtml
                </div>
              </body>
            </html>
        """.trimIndent()
    }

    private fun buildActionLinks(request: ApprovalRequest): List<Pair<String, String>> {
        val baseUrl = AppConfig.approvalActionBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank() || request.actionToken.isBlank()) return emptyList()

        return listOf(
            "Accept" to buildActionUrl(baseUrl, request, "approve"),
            "Reject" to buildActionUrl(baseUrl, request, "reject")
        )
    }

    private fun buildActionUrl(
        baseUrl: String,
        request: ApprovalRequest,
        action: String
    ): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator" + listOf(
            "requestId" to request.requestId,
            "token" to request.actionToken,
            "action" to action
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun tableRow(label: String, value: String): String {
        return """
            <tr>
              <td style="padding:10px 0 6px;font-size:13px;font-weight:600;color:#64748B;width:160px;vertical-align:top;">${escapeHtml(label)}</td>
              <td style="padding:10px 0 6px;font-size:15px;color:#111827;vertical-align:top;">${escapeHtml(value)}</td>
            </tr>
        """.trimIndent()
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
