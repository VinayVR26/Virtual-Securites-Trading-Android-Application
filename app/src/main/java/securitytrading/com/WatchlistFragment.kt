package securitytrading.com

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.DecimalFormat

class WatchlistFragment : Fragment() {
    val fAuth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"
    var stockSymbol = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_watchlist, container, false)
        val tableLayout = view.findViewById<TableLayout>(R.id.tableLayout)

        fun addTableRow(tableLayout: TableLayout, text1: String, text2: String, text3: String) {
            val tableRow = TableRow(requireContext())
            val stockSymbolTextView = TextView(requireContext())
            val priceTextView = TextView(requireContext())
            val percentageChangeTextView = TextView(requireContext())

            stockSymbolTextView.text = text1
            priceTextView.text = text2
            percentageChangeTextView.text = text3

            stockSymbolTextView.gravity = Gravity.CENTER
            priceTextView.gravity = Gravity.CENTER
            percentageChangeTextView.gravity = Gravity.CENTER

            tableRow.addView(stockSymbolTextView)
            tableRow.addView(priceTextView)
            tableRow.addView(percentageChangeTextView)

            tableLayout.addView(tableRow)
        }

        fun getCurrentPrice(favoritesArray: List<String>) {
            val decimalFormat = DecimalFormat("#.00")
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    for (stock in favoritesArray) {
                        stockSymbol = stock
                        val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${stockSymbol}&token=${finhubApiKey}"
                        val quoteResponse = URL(quoteUrl).readText()
                        val quoteJson = JSONObject(quoteResponse)
                        val c = quoteJson.getDouble("c").toFloat()
                        val dp = decimalFormat.format(quoteJson.getDouble("dp")).toFloat()

                        withContext(Dispatchers.Main) {
                            addTableRow(tableLayout, stockSymbol, c.toString(), dp.toString())
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.d("error", "Error fetching data")
                    }
                    e.printStackTrace()
                }
            }
        }

        val userEmail = fAuth.currentUser?.email
        if (userEmail != null) {
            db.collection("users").document(userEmail).get()
                .addOnSuccessListener { documentSnapshot: DocumentSnapshot? ->
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val favoritesArray = documentSnapshot.get("favorites") as? List<String> ?: emptyList()
                        getCurrentPrice(favoritesArray)
                    }
                }
        }

        return view
    }
}


