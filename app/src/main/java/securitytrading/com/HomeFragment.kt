package securitytrading.com

import GeneralNewsAdapter
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class HomeFragment : Fragment() {

    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var lineData: HashMap<String, Float>? = null
    var investedStocks: MutableList<String> = mutableListOf()

    data class NewsResponse(
        val headline: String,
        val datetime: Long,
        val image: String,
        val summary: String,
        val url: String
    )

    data class StockInfo(val symbol: String, val price: Float, val percentageChange: Float)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.generalNewsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val fundsForTradingTextView = view.findViewById<TextView>(R.id.fundsForTradingTextView)
        val totalCashFlowTextView = view.findViewById<TextView>(R.id.totalCashFlowTextView)

        val lineChart = view.findViewById<LineChart>(R.id.tcfGrowth)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userEmail = currentUser.email
            if (userEmail != null) {
                db.collection("users").document(userEmail).get().addOnSuccessListener { documentSnapshot: DocumentSnapshot? ->
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val fundsForTrading = documentSnapshot.getDouble("fundsForTrading")
                        val totalCashFlow = documentSnapshot.get("totalCashFlow").toString().toFloat()
                        investedStocks = documentSnapshot.get("investedStocks") as? MutableList<String> ?: mutableListOf()

                        fetchAndSetPrice(investedStocks)

                        fundsForTradingTextView.text = String.format("Funds for Trading: $%.2f", fundsForTrading)
                        totalCashFlowTextView.text = "Total Cash Flow: $totalCashFlow"

                        val currentDate = LocalDate.now()
                        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        val formattedDate = currentDate.format(dateFormatter)


                        if (documentSnapshot.contains("lineData")) {
                            lineData = documentSnapshot.get("lineData") as HashMap<String, Float>?
                        }

                        if (lineData == null) {
                            lineData = HashMap()
                        }

                        if (!lineData?.containsKey(formattedDate)!!) {
                            lineData!![formattedDate] = totalCashFlow
                            documentSnapshot.reference.update("lineData", lineData).addOnCompleteListener { updateTask ->
                                        if (updateTask.isSuccessful) {
                                            createLineChart(lineChart, lineData)
                                        }
                                    }
                                } else {
                                    createLineChart(lineChart, lineData)
                                }
                            }
                        }
                    }
                }
        fetchGeneralNews(recyclerView)
        return view
    }

    fun fetchAndSetPrice(investedStocks: MutableList<String>) {
        GlobalScope.launch(Dispatchers.IO) {
            val stockInfoList = mutableListOf<StockInfo>()
            try {
                for (stock in investedStocks) {
                    val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${stock}&token=${finhubApiKey}"
                    val quoteResponse = URL(quoteUrl).readText()
                    val quoteJson = JSONObject(quoteResponse)
                    val currentPrice = quoteJson.getDouble("c").toFloat()
                    val percentageChange = quoteJson.getDouble("dp").toFloat()

                    val stockInfo = StockInfo(stock, currentPrice, percentageChange)
                    stockInfoList.add(stockInfo)
                }

                stockInfoList.sortByDescending { it.percentageChange }


                withContext(Dispatchers.Main) {
                    val bestAndWorstPerformingContentsTextView = view?.findViewById<TextView>(R.id.bestAndWorstPerformingContents)

                    if (stockInfoList.size > 1) {
                        val bestPerformers = stockInfoList.take(1).joinToString("\n") {
                            val formattedChange = if (it.percentageChange >= 0) {
                                "+${String.format("%.2f", it.percentageChange)}%"
                            } else {
                                "${String.format("%.2f", it.percentageChange)}%"
                            }
                            "${it.symbol}: $formattedChange"
                        }

                        val worstPerformers = stockInfoList.takeLast(1).joinToString("\n") {
                            val formattedChange = if (it.percentageChange >= 0) {
                                "+${String.format("%.2f", it.percentageChange)}%"
                            } else {
                                "${String.format("%.2f", it.percentageChange)}%"
                            }
                            "${it.symbol}: $formattedChange"
                        }
                        val bestAndWorstPerformingText = "Best Performer:\n$bestPerformers\n\nWorst Performer:\n$worstPerformers"
                        bestAndWorstPerformingContentsTextView?.text = bestAndWorstPerformingText

                    } else if (stockInfoList.size == 1) {

                        val stockData = stockInfoList.take(1).joinToString("\n") {
                            val formattedChange = if (it.percentageChange >= 0) {
                                "+${String.format("%.2f", it.percentageChange)}%"
                            } else {
                                "${String.format("%.2f", it.percentageChange)}%"
                            }
                            "${it.symbol}: $formattedChange"
                        }

                        bestAndWorstPerformingContentsTextView?.text = stockData

                    } else {
                        bestAndWorstPerformingContentsTextView?.text = "Nothing to display"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                }
                e.printStackTrace()
            }
        }
    }

    private fun createLineChart(lineChart: LineChart, lineData: HashMap<String, Float>?) {
        if (lineData != null) {
            val entries = ArrayList<Entry>()
            val dateList = ArrayList<String>()
            var smallest = Double.POSITIVE_INFINITY.toFloat()

            var earliestDate: LocalDate? = null
            for ((date, value) in lineData) {
                entries.add(Entry(dateList.size.toFloat(), value))
                dateList.add(date)
                val dateObj = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                if (earliestDate == null || dateObj.isBefore(earliestDate)) {
                    earliestDate = dateObj
                }

                if (value < smallest) {
                    smallest = value
                }
            }

            val dataSet = LineDataSet(entries, "Total Cash Flow")
            dataSet.color = Color.BLUE
            dataSet.setCircleColor(Color.RED)
            dataSet.setDrawValues(false)

            val lineDataSet = LineData(dataSet)
            lineChart.data = lineDataSet

            lineChart.setDrawBorders(true)
            lineChart.setBorderColor(Color.BLACK)
            lineChart.setBorderWidth(1f)


            val xAxis = lineChart.xAxis
            xAxis.valueFormatter = IndexAxisValueFormatter(dateList)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelCount = dateList.size
            xAxis.setDrawGridLines(false)


            if (earliestDate != null) {
                val earliestDateStr = earliestDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                val minValue = (lineData[earliestDateStr] as? Double)?.toFloat() ?: 0f
                val yAxis = lineChart.axisLeft
                yAxis.axisMinimum = minValue
                yAxis.setDrawGridLines(false)
            }

            lineChart.axisRight.isEnabled = false
            lineChart.description.isEnabled = false
            lineChart.legend.isEnabled = false
            lineChart.invalidate()
        }
    }

    fun fetchGeneralNews(recyclerView: RecyclerView) {
        GlobalScope.launch(Dispatchers.IO) {
            val baseUrl = "https://finnhub.io/api/v1"
            val detailsUrl = "$baseUrl/news?category=general&token=$finhubApiKey"
            try {
                val jsonString = URL(detailsUrl).readText()
                val newsList = parseNewsJson(jsonString)

                withContext(Dispatchers.Main) {
                    val adapter = GeneralNewsAdapter(newsList)
                    recyclerView.adapter = adapter
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseNewsJson(jsonString: String): List<NewsResponse> {
        val newsList = mutableListOf<NewsResponse>()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val headline = jsonObject.getString("headline")
            val datetime = jsonObject.getLong("datetime")
            val image = jsonObject.getString("image")
            val summary = jsonObject.getString("summary")
            val url = jsonObject.getString("url")
            val news = NewsResponse(headline, datetime, image, summary, url)
            newsList.add(news)
        }
        return newsList
    }



}