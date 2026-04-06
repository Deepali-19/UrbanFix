package com.example.urban.bottomNavigation.drawer.FO

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.urban.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class FieldOfficerFragment : Fragment(R.layout.fragment_field_officer) {

    private lateinit var recyclerView: RecyclerView
    private val list = ArrayList<FieldOfficer>()
    private lateinit var adapter: FieldOfficerAdapter

    private val database = FirebaseDatabase.getInstance().reference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        recyclerView = view.findViewById(R.id.recyclerView)

        adapter = FieldOfficerAdapter(list) { officer ->

            val fragment = OfficerDetailFragment.newInstance(officer.uid)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadOfficers()
    }

    private fun loadOfficers() {

        database.child("Users")
            .orderByChild("role")
            .equalTo("Field Officer")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    list.clear()

                    for (data in snapshot.children) {

                        val officer = data.getValue(FieldOfficer::class.java) ?: continue
                        officer.uid = data.key!!

                        list.add(officer)

                        loadComplaintCount(officer)
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadComplaintCount(officer: FieldOfficer) {

        database.child("Complaints")
            .orderByChild("allottedOfficerId")
            .equalTo(officer.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    var count = 0

                    for (item in snapshot.children) {

                        val status = item.child("status").getValue(Int::class.java) ?: 0

                        if (status == 1) { // In Progress only
                            count++
                        }
                    }

                    officer.inProgressCount = count
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

}
