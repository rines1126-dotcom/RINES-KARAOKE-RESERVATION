
package com.rines.karaokereservation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream

data class Song(
    val number: String,
    val title: String,
    val artist: String,
    val category: String,
    val type: String
)

class MainActivity : AppCompatActivity() {

    private val database by lazy {
        FirebaseDatabase.getInstance().reference.child("reservations")
    }

    private val deviceId by lazy {
        val prefs = getSharedPreferences("reservation", MODE_PRIVATE)
        prefs.getString("device_id", null)
            ?: java.util.UUID.randomUUID().toString()
                .also { prefs.edit().putString("device_id", it).apply() }
    }

    private lateinit var searchField: EditText
    private lateinit var singerName: EditText
    private lateinit var selectedSongText: TextView
    private lateinit var songList: LinearLayout
    private lateinit var reservationList: LinearLayout

    private var selectedSong: Song? = null
    private var songDb: SQLiteDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOpeningScreen()
    }

    private fun showOpeningScreen() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(5, 10, 25))
            setPadding(24, 24, 24, 24)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.rines_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        splash.addView(logo, LinearLayout.LayoutParams(260, 260))

        val title = TextView(this).apply {
            text = "RINES KARAOKE"
            textSize = 26f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }
        splash.addView(title)

        val subtitle = TextView(this).apply {
            text = "RESERVATION"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        splash.addView(subtitle)

        setContentView(splash)

        Handler(Looper.getMainLooper()).postDelayed({
            openSongDatabase()
            showMainScreen()
        }, 2500)
    }

    private fun openSongDatabase() {
        try {
            val dbFile = File(filesDir, "songhub_songbook.db")

            if (!dbFile.exists()) {
                assets.open("songhub_songbook.db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            songDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Database error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showMainScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.WHITE)
        }

        val heading = TextView(this).apply {
            text = "RINES KARAOKE RESERVATION"
            textSize = 22f
            setTextColor(Color.rgb(0, 70, 130))
            setPadding(0, 10, 0, 15)
        }
        layout.addView(heading)

        val songHeading = TextView(this).apply {
            text = "SONGBOOK"
            textSize = 19f
            setTextColor(Color.BLACK)
        }
        layout.addView(songHeading)

        searchField = EditText(this).apply {
            hint = "Search number, title or artist..."
            textSize = 16f
            singleLine = true
        }
        layout.addView(searchField)

        val songScroll = ScrollView(this)
        songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        songScroll.addView(songList)

        layout.addView(
            songScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                350
            )
        )

        val selectedHeading = TextView(this).apply {
            text = "SELECTED SONG"
            textSize = 17f
            setTextColor(Color.BLACK)
            setPadding(0, 15, 0, 5)
        }
        layout.addView(selectedHeading)

        selectedSongText = TextView(this).apply {
            text = "No song selected"
            textSize = 16f
            setTextColor(Color.rgb(0, 90, 150))
            setPadding(0, 5, 0, 10)
        }
        layout.addView(selectedSongText)

        singerName = EditText(this).apply {
            hint = "Singer Name / Nickname"
            textSize = 16f
        }
        layout.addView(singerName)

        val reserveButton = Button(this).apply {
            text = "RESERVE SONG"
            setOnClickListener { reserveSong() }
        }
        layout.addView(reserveButton)

        val reservationHeading = TextView(this).apply {
            text = "MY RESERVATIONS"
            textSize = 19f
            setTextColor(Color.BLACK)
            setPadding(0, 20, 0, 10)
        }
        layout.addView(reservationHeading)

        reservationList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(reservationList)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchSongs(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchSongs("")
        listenToReservations()
    }

    private fun searchSongs(keyword: String) {
        songList.removeAllViews()

        val db = songDb ?: return

        try {
            val search = "%${keyword.trim()}%"

            val cursor = db.rawQuery(
                """
                SELECT number, title, artist, category, type
                FROM songs
                WHERE CAST(number AS TEXT) LIKE ?
                   OR title LIKE ?
                   OR artist LIKE ?
                ORDER BY number
                LIMIT 100
                """.trimIndent(),
                arrayOf(search, search, search)
            )

            if (cursor.count == 0) {
                val empty = TextView(this).apply {
                    text = "No songs found."
                    textSize = 16f
                    setPadding(8, 15, 8, 15)
                }
                songList.addView(empty)
            }

            while (cursor.moveToNext()) {
                val song = Song(
                    number = cursor.getString(0) ?: "",
                    title = cursor.getString(1) ?: "",
                    artist = cursor.getString(2) ?: "",
                    category = cursor.getString(3) ?: "",
                    type = cursor.getString(4) ?: ""
                )

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 10, 8, 10)
                    setBackgroundColor(Color.rgb(240, 245, 250))
                }

                val info = TextView(this).apply {
                    text = "${song.number} - ${song.title}\n${song.artist}"
                    textSize = 15f
                    setTextColor(Color.BLACK)
                }
                row.addView(info)

                val selectButton = Button(this).apply {
                    text = "PILIIN"
                    setOnClickListener {
                        selectedSong = song
                        selectedSongText.text =
                            "${song.number} - ${song.title}\n${song.artist}"
                        Toast.makeText(
                            this@MainActivity,
                            "Song selected!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                row.addView(selectButton)

                songList.addView(row)

                val divider = TextView(this).apply {
                    text = " "
                    setBackgroundColor(Color.LTGRAY)
                }
                songList.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                )
            }

            cursor.close()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Song search error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun reserveSong() {
        val song = selectedSong
        val singer = singerName.text.toString().trim()

        if (song == null) {
            Toast.makeText(
                this,
                "Please select a song first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (singer.isEmpty()) {
            singerName.error = "Enter singer name"
            return
        }

        val reservation = hashMapOf(
            "songNumber" to song.number,
            "songTitle" to song.title,
            "artist" to song.artist,
            "singerName" to singer,
            "deviceId" to deviceId,
            "status" to "Waiting",
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.push().setValue(reservation)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Reservation sent!",
                    Toast.LENGTH_SHORT
                ).show()

                selectedSong = null
                selectedSongText.text = "No song selected"
                singerName.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun listenToReservations() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reservationList.removeAllViews()

                for (item in snapshot.children) {
                    val owner = item.child("deviceId")
                        .getValue(String::class.java)

                    if (owner != deviceId) continue

                    val title = item.child("songTitle")
                        .getValue(String::class.java) ?: ""
                    val number = item.child("songNumber")
                        .getValue(String::class.java) ?: ""
                    val status = item.child("status")
                        .getValue(String::class.java) ?: "Waiting"

                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 8, 0, 8)
                    }

                    val info = TextView(this@MainActivity).apply {
                        text = "$number - $title\nStatus: $status"
                        textSize = 16f
                        setTextColor(Color.BLACK)
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
                            text = "STOP"
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

    override fun onDestroy() {
        super.onDestroy()
        songDb?.close()
    }
}
