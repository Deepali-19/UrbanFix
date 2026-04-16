package com.example.urban.bottomNavigation.alert

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

class UrbanFixMessagingService : FirebaseMessagingService() {

    // This function saves the latest FCM device token for the logged-in user.
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(uid)
            .child("deviceToken")
            .setValue(token)
    }

    // This function receives a remote message, stores it locally, and shows a notification.
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val alert = AlertItem(
            id = remoteMessage.messageId ?: UUID.randomUUID().toString(),
            title = data["title"] ?: remoteMessage.notification?.title ?: "Urban Fix Alert",
            body = data["body"] ?: remoteMessage.notification?.body ?: "A new complaint update is available.",
            type = data["type"] ?: "Complaint Update",
            timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            complaintKey = data["complaintKey"] ?: data["complaintId"].orEmpty(),
            complaintDisplayId = data["complaintDisplayId"] ?: data["complaintId"].orEmpty()
        )

        AlertStorage.addAlert(applicationContext, alert)
        AlertNotifier.show(applicationContext, alert)
    }
}
