package securitytrading.com

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
//import android.widget.CalendarView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.applandeo.materialcalendarview.CalendarView
import com.google.android.material.datepicker.DayViewDecorator
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class CalendarFragment : Fragment() {
    private lateinit var calendarView: CalendarView
    private lateinit var addEventButton: FloatingActionButton
    private var selectedDateMillis: Long = 0
    private var selectedDateWithEvents: String? = null
    val fAuth = FirebaseAuth.getInstance()
    val fStore = FirebaseFirestore.getInstance()
    private var alertDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        addEventButton = view.findViewById(R.id.addEventButton)

        calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                selectedDateMillis = eventDay.calendar.timeInMillis
            }
        })

        // Set a click listener for the addEventButton
        addEventButton.setOnClickListener {
            val intent = Intent(activity, AddEventActivity::class.java)
            startActivityForResult(intent, ADD_EVENT_REQUEST_CODE)
        }

        fetchEventDataAndDecorateCalendar()

        return view
    }

    private fun fetchEventDataAndDecorateCalendar() {
        val email = fAuth.currentUser?.email.toString()
        if (email.isNotBlank()) {
            val userDocRef = fStore.collection("users").document(email)

            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                val calendarEvents = documentSnapshot.get("calendarEvents") as? Map<String, Any>

                val events = ArrayList<EventDay>()

                if (calendarEvents != null) {
                    for (dateKey in calendarEvents.keys) {
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        val date = dateFormat.parse(dateKey)
                        val calendar = Calendar.getInstance()
                        calendar.time = date

                        val iconDrawable = getEventIcon(dateKey)
                        val eventDay = EventDay(calendar, iconDrawable)
                        events.add(eventDay)
                    }
                }

                calendarView.setEvents(events)
                calendarView.setOnDayClickListener(object : OnDayClickListener {
                    override fun onDayClick(eventDay: EventDay) {
                        selectedDateMillis = eventDay.calendar.timeInMillis
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        val selectedDate = dateFormat.format(Date(selectedDateMillis))

                        if (calendarEvents?.containsKey(selectedDate) == true) {
                            selectedDateWithEvents = selectedDate
                            showEventDetailsPopup(selectedDate)
                        }
                    }
                })
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Failed to fetch event data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getEventIcon(dateKey: String): Drawable {
        return when (dateKey) {
            "01-01-2023" -> ContextCompat.getDrawable(requireContext(), R.drawable.baseline_add_circle)!!
            "02-01-2023" -> ContextCompat.getDrawable(requireContext(), R.drawable.baseline_add_circle)!!
            else -> ContextCompat.getDrawable(requireContext(), R.drawable.baseline_add_circle)!!
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_EVENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val eventTitle = data?.getStringExtra("eventTitle")
            val eventDateTime = data?.getLongExtra("eventDateTime", 0)

            if (eventTitle != null && eventDateTime != null) {
                saveEventToFirestore(eventTitle, eventDateTime)

                fetchEventDataAndDecorateCalendar()
            }
        }
    }


    private fun saveEventToFirestore(
        eventTitle: String,
        eventDateTime: Long
    ) {
        val email = fAuth.currentUser?.email.toString()
        if (email != null) {
            val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                .format(Date(eventDateTime))

            val eventData = mapOf(
                "title" to eventTitle,
                "dateTime" to eventDateTime
            )

            val userDocRef = fStore.collection("users").document(email)

            userDocRef.update(
                "calendarEvents.$formattedDate", FieldValue.arrayUnion(eventData)
            ).addOnSuccessListener {
                Toast.makeText(context, "Event saved successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Failed to save event: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showEventDetailsPopup(selectedDate: String) {
        val email = fAuth.currentUser?.email.toString()
        if (email.isNotBlank()) {
            val userDocRef = fStore.collection("users").document(email)

            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                val calendarEvents = documentSnapshot.get("calendarEvents") as? Map<String, Any>

                val eventsForSelectedDate = calendarEvents?.get(selectedDate) as? List<Map<String, Any>>

                if (eventsForSelectedDate != null) {
                    val popupView = layoutInflater.inflate(R.layout.event_details_popup, null)
                    val popupTitle = popupView.findViewById<TextView>(R.id.popupTitle)
                    val eventDetailsLayout= popupView.findViewById<LinearLayout>(R.id.eventDetailsLayout)
                    val closeButton = popupView.findViewById<Button>(R.id.closeButton)
                    val completeButton = popupView.findViewById<Button>(R.id.completeButton)

                    popupTitle.text = "Events for $selectedDate"

                    val eventCheckBoxes = mutableListOf<CheckBox>()

                    for (event in eventsForSelectedDate) {
                        val title = event["title"] as? String ?: ""
                        val timeMillis = event["dateTime"] as? Long ?: 0

                        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val eventTime = dateFormat.format(Date(timeMillis))

                        val checkbox = CheckBox(requireContext())
                        checkbox.text = "Title: $title\nTime: $eventTime"
                        eventDetailsLayout.addView(checkbox)
                        eventCheckBoxes.add(checkbox)
                    }

                    completeButton.setOnClickListener {
                        val eventsToRemove = mutableListOf<Map<String, Any>>()
                        for ((index, checkbox) in eventCheckBoxes.withIndex()) {
                            if (checkbox.isChecked) {
                                eventsToRemove.add(eventsForSelectedDate[index])
                            }
                        }

                        if (eventsToRemove.size == eventsForSelectedDate.size) {
                            userDocRef.update("calendarEvents.$selectedDate", FieldValue.delete())
                        } else {
                            for (eventToRemove in eventsToRemove) {
                                val eventDateTime = eventToRemove["dateTime"] as? Long ?: 0
                                val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                    .format(Date(eventDateTime))
                                userDocRef.update(
                                    "calendarEvents.$formattedDate",
                                    FieldValue.arrayRemove(eventToRemove)
                                )
                            }
                        }

                        fetchEventDataAndDecorateCalendar()
                        alertDialog?.dismiss()
                    }

                    val alertDialogBuilder = AlertDialog.Builder(requireContext())
                    alertDialogBuilder.setView(popupView)
                    alertDialogBuilder.setCancelable(false)
                    alertDialog = alertDialogBuilder.create()


                    closeButton.setOnClickListener {
                        alertDialog?.dismiss()
                    }

                    alertDialog?.show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Failed to fetch event details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ADD_EVENT_REQUEST_CODE = 1
    }
}
