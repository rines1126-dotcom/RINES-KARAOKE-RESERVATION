package com.rines.karaokereservation

import android.os.Bundle
import android.widget.*
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private val database by lazy {
        FirebaseDatabase.getInstance().reference.child("reservations")
    }

    private val deviceId by lazy {
        val prefs = getSharedPreferences("reservation", MODE_PRIVATE)
        prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString()
            .also { prefs.edit().putString("device_id", it).apply() }
    }

    private lateinit var songNumber: EditText
    private lateinit var songTitle: EditText
    private lateinit var singerName: EditText
    private lateinit var reservationList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun addField(hintText: String): EditText {
            val field = EditText(this).apply {
                hint = hintText
                textSize = 16f
            }
            layout.addView(field)
            return field
        }

        val heading = TextView(this).apply {
            text = "RINES KARAOKE RESERVATION"
            textSize = 22f
            setPadding(0, 12, 0, 20)
        }
        layout.addView(heading)

        songNumber = addField("Song Number")
        songTitle = addField("Song Title")
        singerName = addField("Singer Name")

        val reserveButton = Button(this).apply {
            text = "RESERVE SONG"
            setOnClickListener { reserveSong() }
        }
        layout.addView(reserveButton)

        val listHeading = TextView(this).apply {
            text = "MY RESERVATIONS"
            textSize = 18f
            setPadding(0, 20, 0, 10)
        }
        layout.addView(listHeading)

        reservationList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(reservationList)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        listenToReservations()
    }

    private fun reserveSong() {
        val number = songNumber.text.toString().trim()
        val title = songTitle.text.toString().trim()
        val singer = singerName.text.toString().trim()

        if (number.isEmpty() || title.isEmpty() || singer.isEmpty()) {
            Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val reservation = hashMapOf(
            "songNumber" to number,
            "songTitle" to title,
            "singerName" to singer,
            "deviceId" to deviceId,
            "status" to "Waiting",
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.push().setValue(reservation)
            .addOnSuccessListener {
                Toast.makeText(this, "Reservation sent!", Toast.LENGTH_SHORT).show()
                songNumber.text.clear()
                songTitle.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun listenToReservations() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reservationList.removeAllViews()

                for (item in snapshot.children) {
                    val owner = item.child("deviceId").getValue(String::class.java)
                    if (owner != deviceId) continue

                    val title = item.child("songTitle").getValue(String::class.java) ?: ""
                    val number = item.child("songNumber").getValue(String::class.java) ?: ""
                    val status = item.child("status").getValue(String::class.java) ?: "Waiting"

                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 8, 0, 8)
                    }

                    val info = TextView(this@MainActivity).apply {
                        text = "$number - $title\nStatus: $status"
                        textSize = 16f
                    }
                    row.addView(info)

                    if (status == "Waiting") {
                        val cancel = Button(this@MainActivity).apply {
                            text = "CANCEL"
                            setOnClickListener {
                                item.ref.removeValue()
                            }
                        }
                        row.addView(cancel)
                    } else if (status == "Playing") {
                        val stop = Button(this@MainActivity).apply {
                            text = "STOP REQUEST"
                            setOnClickListener {
                                item.ref.child("stopRequested").setValue(true)
                                Toast.makeText(
                                    this@MainActivity,
                                    "Stop request sent to operator.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        row.addView(stop)
                    }

                    reservationList.addView(row)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}
