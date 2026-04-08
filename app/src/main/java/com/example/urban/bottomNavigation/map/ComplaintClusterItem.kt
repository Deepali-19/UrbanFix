package com.example.urban.bottomNavigation.map

import com.example.urban.bottomNavigation.complaint.Complaint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

data class ComplaintClusterItem(
    val complaint: Complaint
) : ClusterItem {

    override fun getPosition(): LatLng = LatLng(complaint.latitude, complaint.longitude)

    override fun getTitle(): String = complaint.title.ifBlank { "Complaint" }

    override fun getSnippet(): String = complaint.location.ifBlank { "Location not available" }

    override fun getZIndex(): Float = 0f

    fun complaintKey(): String = complaint.firebaseKey.ifBlank { complaint.complaintId }
}
