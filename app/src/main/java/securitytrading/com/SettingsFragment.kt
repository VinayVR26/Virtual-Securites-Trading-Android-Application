package securitytrading.com

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class SettingsFragment : Fragment() {

    private val fAuth = FirebaseAuth.getInstance()
    private val fStore = FirebaseFirestore.getInstance()
    private val currentUser = fAuth.currentUser
    var selectedOption = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)

        val timeFrameSpinner: Spinner = root.findViewById(R.id.timeFrameSpinner)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.time_frame_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            timeFrameSpinner.adapter = adapter
        }

        timeFrameSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                selectedOption = timeFrameSpinner.getItemAtPosition(position).toString()
                when (selectedOption) {
                    "day" -> {
                        displayEventCount(Calendar.DAY_OF_YEAR)
                    }
                    "month" -> {
                        displayEventCount(Calendar.MONTH)
                    }
                    "year" -> {
                        displayEventCount(Calendar.YEAR)
                    }
                }

                if (currentUser != null) {
                    val userDocRef = fStore.collection("users").document(currentUser.email.toString())
                    val data = HashMap<String, Any>()
                    data["reminderOption"] = selectedOption
                    userDocRef.update(data)
                        .addOnSuccessListener {
                            Toast.makeText(
                                requireContext(),
                                "Reminder Option saved: $selectedOption",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireContext(),
                                "Failed to save Reminder Option: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
            }
        }

        return root
    }

    private fun displayEventCount(timeFrame: Int) {
        val email = fAuth.currentUser?.email.toString()
        if (email.isNotBlank()) {
            val userDocRef = fStore.collection("users").document(email)

            val currentDate = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val formattedDate = dateFormat.format(currentDate.time)

            var eventsInTimeFrame: Map<String, Any>? = null

            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                val calendarEvents = documentSnapshot.get("calendarEvents") as? Map<String, Any>

                if (calendarEvents != null) {
                    eventsInTimeFrame = calendarEvents.filter { (dateKey, _) ->
                        val date = dateFormat.parse(dateKey)
                        val calendar = Calendar.getInstance()
                        calendar.time = date
                        currentDate.get(timeFrame) == calendar.get(timeFrame)
                    }

                    var eventCount = eventsInTimeFrame?.size ?: 0
                    Toast.makeText(
                        requireContext(),
                        "Number of events in the selected time frame: $eventCount",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (selectedOption == "none") {
                        eventCount = 0
                    }

                    (activity as MainActivity).updateEventCountSubscript(eventCount)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Failed to fetch event data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}