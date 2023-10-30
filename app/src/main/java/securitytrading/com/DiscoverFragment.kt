package securitytrading.com

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class DiscoverFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var existingStocks: List<HashMap<String, Any>> = emptyList()
    var existingWaitStocks: MutableList<HashMap<String, Any>> = mutableListOf()
    var cancelledStocks: MutableList<HashMap<String, Any>> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_discover, container, false)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userEmail = currentUser.email
            if (userEmail != null) {
                db.collection("users").document(userEmail).get().addOnSuccessListener { documentSnapshot: DocumentSnapshot? ->
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        existingStocks = documentSnapshot.get("stocks") as? List<HashMap<String, Any>>?: emptyList()
                        existingWaitStocks = documentSnapshot.get("waitStocks") as? MutableList<HashMap<String, Any>>?: mutableListOf()
                        cancelledStocks = documentSnapshot.get("cancelStocks") as? MutableList<HashMap<String, Any>>?: mutableListOf()
                        var mergedList = existingStocks + existingWaitStocks


                        mergedList = mergedList.sortedWith(compareByDescending {
                            val dateString = it["date"].toString()
                            val dateFormat = SimpleDateFormat("ddMMyyyyHHmmss", Locale.US)
                            val date = dateFormat.parse(dateString)
                            date
                        })

                        displayData(mergedList)


                    }
                }
            }
        }

        return view
    }

    @SuppressLint("SuspiciousIndentation")
    private fun displayData(mergedList: List<HashMap<String, Any>>) {
        val tableLayout = requireView().findViewById<TableLayout>(R.id.tableLayout)
        val headerRow = requireView().findViewById<TableRow>(R.id.tableHeader)
        tableLayout.removeAllViews()
        tableLayout.addView(headerRow)

        for (item in mergedList) {
            val tableRow = TableRow(requireContext())

            val order = item["order"] as String
            val stockSymbol = item["stockSymbol"] as String
            val limitPrice = if (order == "WAITING") item["limitPrice"] as Double else 0.0
            val unitsBoughtInTransaction = item["unitsBoughtInTransaction"] as Long
            val unitsSoldInTransaction = item["unitsSoldInTransaction"] as Long

            val textView1 = TextView(requireContext())
            val textView2 = TextView(requireContext())

            when (order) {
                "FILLED" -> {
                    val text1 = "$stockSymbol (${item["pricePerUnit"]})\n"
                    val text2 = if (unitsBoughtInTransaction > 0) {
                        "Buy - $unitsBoughtInTransaction"
                    } else {
                        "Sell - $unitsSoldInTransaction"
                    }
                    textView1.text = "$text1     $text2"

                    val dateStr = item["date"] as String
                    val dateFormat = SimpleDateFormat("ddMMyyyyHHmmss", Locale.US)
                    val date = dateFormat.parse(dateStr)
                    val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(date)

                    textView2.text = "$formattedDate"
                    tableRow.addView(textView1)
                    tableRow.addView(textView2)
                }
                "WAITING" -> {
                    val text1 = "$stockSymbol (${item["pricePerUnit"]})\n"
                    val text2 = if (unitsBoughtInTransaction > 0) {
                        "Buy - $unitsBoughtInTransaction"
                    } else {
                        "Sell - $unitsSoldInTransaction"
                    }

                    textView1.text = "$text1     $text2 (WAITING)"
                    val dateStr = item["date"] as String
                    val dateFormat = SimpleDateFormat("ddMMyyyyHHmmss", Locale.US)
                    val date = dateFormat.parse(dateStr)
                    val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(date)

                    textView2.text = "$formattedDate"

                    val tableRowWithButton = TableRow(requireContext())
                    tableRowWithButton.addView(textView1)
                    tableRowWithButton.addView(textView2)

                    val cancelButton = Button(requireContext())
                    cancelButton.text = "CANCEL"
                    cancelButton.setOnClickListener {

                        val dateToCancelString = item["date"] as String

                        val itemToCancel = existingWaitStocks.find {
                            val existingWaitDate = it["date"] as String
                            existingWaitDate == dateToCancelString
                        }

                        if (itemToCancel != null) {
                            existingWaitStocks.remove(itemToCancel)
                            cancelledStocks.add(itemToCancel)


                            val updateData = hashMapOf(
                                "waitStocks" to existingWaitStocks,
                                "cancelStocks" to cancelledStocks,
                            )

                            val usersEmail = auth.currentUser?.email
                                if (usersEmail != null) {
                                    val userDocument = db.collection("users").document(usersEmail)
                                    userDocument.get().addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            userDocument.update(updateData as Map<String, Any>).addOnCompleteListener { updateTask ->
                                                if (updateTask.isSuccessful) {
                                                    db.collection("users").document(usersEmail).get().addOnSuccessListener { documentSnapshot: DocumentSnapshot? ->
                                                        if (documentSnapshot != null && documentSnapshot.exists()) {
                                                            existingStocks = documentSnapshot.get("stocks") as? List<HashMap<String, Any>> ?: emptyList()
                                                            existingWaitStocks = documentSnapshot.get("waitStocks") as? MutableList<HashMap<String, Any>> ?: mutableListOf()
                                                            cancelledStocks = documentSnapshot.get("cancelStocks") as? MutableList<HashMap<String, Any>> ?: mutableListOf()
                                                            val mergedList = existingStocks + existingWaitStocks
                                                            displayData(mergedList)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                    tableRowWithButton.addView(cancelButton)

                    tableLayout.addView(tableRowWithButton)
                }
            }

            val textView3 = TextView(requireContext())
            if (order == "FILLED") {
                if (unitsBoughtInTransaction > 0) {
                    textView3.text = "Expenditure - ${item["expenditureOfTransaction"]}"
                } else {
                    textView3.text = "Returns - ${item["returnsFromTransaction"]}"
                }
            }

            tableRow.addView(textView3)
            tableLayout.addView(tableRow)
        }
    }
}