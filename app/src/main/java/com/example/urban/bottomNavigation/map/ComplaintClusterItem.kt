package com.example.urban.bottomNavigation.map

import com.example.urban.bottomNavigation.complaint.Complaint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

// This model wraps one complaint so Google Maps clustering can work with complaint data directly.
data class ComplaintClusterItem(
    val complaint: Complaint
) : ClusterItem {

    // This function returns the map position used for clustering and marker placement.
    override fun getPosition(): LatLng = LatLng(complaint.latitude, complaint.longitude)

    // This function returns the short title shown for the marker item.
    override fun getTitle(): String = complaint.title.ifBlank { "Complaint" }

    // This function returns the short subtitle shown for the marker item.
    override fun getSnippet(): String = complaint.location.ifBlank { "Location not available" }

    // This function keeps the default marker layering order.
    override fun getZIndex(): Float = 0f

    // This helper returns the best complaint key for opening detail screens.
    fun complaintKey(): String = complaint.firebaseKey.ifBlank { complaint.complaintId }
}
