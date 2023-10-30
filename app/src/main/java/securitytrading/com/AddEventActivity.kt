package securitytrading.com

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEventActivity : AppCompatActivity() {
    private lateinit var eventTitleEditText: EditText
    private lateinit var pickDateTimeButton: Button
    private lateinit var saveEventButton: Button
    private lateinit var cancelButton: Button

    private var selectedDateTimeMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        eventTitleEditText = findViewById(R.id.eventTitle)
        pickDateTimeButton = findViewById(R.id.pickDateTimeButton)
        saveEventButton = findViewById(R.id.saveEventButton)
        cancelButton = findViewById(R.id.cancelButton)

        pickDateTimeButton.setOnClickListener {
            showDateTimePickerDialog()
        }

        saveEventButton.setOnClickListener {
            saveEvent()
        }

        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun showDateTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val datePickerDialog = DatePickerDialog(
            this,
            DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                val timePickerDialog = TimePickerDialog(
                    this,
                    TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                        calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                        selectedDateTimeMillis = calendar.timeInMillis
                        updateDateTimeButtonText()
                    },
                    currentHour,
                    currentMinute,
                    false
                )
                timePickerDialog.show()
            },
            currentYear,
            currentMonth,
            currentDay
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun updateDateTimeButtonText() {
        if (selectedDateTimeMillis != 0L) {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault())
            val formattedDateTime = sdf.format(selectedDateTimeMillis)
            pickDateTimeButton.text = formattedDateTime
        } else {
            pickDateTimeButton.text = "Pick Date and Time"
        }
    }

    private fun saveEvent() {
        val eventTitle = eventTitleEditText.text.toString()

        if (eventTitle.isNotEmpty() && selectedDateTimeMillis > 0) {
            val intent = Intent()
            intent.putExtra("eventTitle", eventTitle)
            intent.putExtra("eventDateTime", selectedDateTimeMillis)

            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}
