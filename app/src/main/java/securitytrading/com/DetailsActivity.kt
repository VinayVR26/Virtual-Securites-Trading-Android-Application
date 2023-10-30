package securitytrading.com

import NewsAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import securitytrading.com.databinding.ActivityDetailsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URL

class DetailsActivity : AppCompatActivity() {

    private lateinit var stockSymbol: String
    val fAuth = FirebaseAuth.getInstance()

    private lateinit var lineChart: LineChart
    private lateinit var selectedFilter: String
    private var previousFilter = "1D"
    private var chartInitialized = false
    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val chartConfig = mapOf(
        "1D" to ChartResolution("1", 1, 0, 0, 0),
        "1W" to ChartResolution("15", 0, 1, 0, 0),
        "1M" to ChartResolution("60", 0, 0, 1, 0),
        "1Y" to ChartResolution("D", 0, 0, 0, 1)
    )

    data class ChartResolution(
        val resolution: String,
        val days: Int,
        val weeks: Int,
        val months: Int,
        val years: Int
    )


    private lateinit var currentPriceTextView: TextView
    private lateinit var priceChangeTextView: TextView
    private lateinit var percentageChangeTextView: TextView

    private lateinit var stockDetailsTextView: TextView

    private lateinit var webSocket: WebSocket

    data class NewsItem(
        val title: String,
        val timePublished: String,
        val bannerImage: String,
        val summary: String,
        val url: String
    )
    private val newsList: MutableList<NewsItem> = mutableListOf()
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var newsRecyclerView: RecyclerView

    private lateinit var binding: ActivityDetailsBinding

    private lateinit var showNewsButton: Button
    private var isNewsVisible = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showNewsButton = findViewById(R.id.showNewsButton)

        showNewsButton.setOnClickListener {
            if (isNewsVisible) {
                newsRecyclerView.visibility = View.GONE
                showNewsButton.text = "Show news"
                isNewsVisible = false
            } else {
                newsRecyclerView.visibility = View.VISIBLE
                showNewsButton.text = "Hide news"
                isNewsVisible = true
            }
        }

        newsRecyclerView = findViewById(R.id.newsRecyclerView)
        newsAdapter = NewsAdapter(newsList)
        newsRecyclerView.layoutManager = LinearLayoutManager(this)
        newsRecyclerView.adapter = newsAdapter

        lineChart = binding.lineChart

        currentPriceTextView = findViewById(R.id.currentPriceTextView)
        priceChangeTextView = findViewById(R.id.priceChangeTextView)
        percentageChangeTextView = findViewById(R.id.percentageChangeTextView)

        stockDetailsTextView = findViewById(R.id.stockDetailsTextView)

        stockSymbol = intent.getStringExtra("symbol") ?: ""

        supportActionBar?.title = stockSymbol

        val starImageView = findViewById<ImageView>(R.id.starImageView)
        starImageView.setOnClickListener {
            toggleFavoriteStock()
        }

        checkFavoriteStatus()

        val stockSymbolTextView = findViewById<TextView>(R.id.stockSymbolTextView)
        stockSymbolTextView.text = stockSymbol
        stockSymbolTextView.visibility = View.VISIBLE

        val button1D = findViewById<Button>(R.id.button1D)
        val button1W = findViewById<Button>(R.id.button1W)
        val button1M = findViewById<Button>(R.id.button1M)
        val button1Y = findViewById<Button>(R.id.button1Y)

        button1D.setOnClickListener {
            selectedFilter = "1D"
            fetchAndSetData(stockSymbol, selectedFilter)
        }

        button1W.setOnClickListener {
            selectedFilter = "1W"
            fetchAndSetData(stockSymbol, selectedFilter)
        }

        button1M.setOnClickListener {
            selectedFilter = "1M"
            fetchAndSetData(stockSymbol, selectedFilter)
        }

        button1Y.setOnClickListener {
            selectedFilter = "1Y"
            fetchAndSetData(stockSymbol, selectedFilter)
        }


        val filters = arrayOf("1D", "1W", "1M", "1Y")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        selectedFilter = "1D"
        fetchAndSetData(stockSymbol, selectedFilter)

        initializeWebSocket(stockSymbol)

        val tradeButton = findViewById<Button>(R.id.tradeButton)
        tradeButton.setOnClickListener {
            val intent = Intent(this@DetailsActivity, TransactionActivity::class.java)

            intent.putExtra("stockSymbol", stockSymbol)

            val pattern = "\\d+\\.?\\d*".toRegex()
            val matchResult = pattern.find(currentPriceTextView.text)
            val numberString = matchResult?.value
            intent.putExtra("unitPrice", numberString)

            startActivity(intent)
        }

        fetchNewsData(stockSymbol)
    }

    fun fetchNewsData(stockSymbol: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=$stockSymbol&apikey=C8T27SI6MYVIZYBS"
                val jsonString = URL(url).readText()
                val jsonObject = JSONObject(jsonString)
                val newsArray = jsonObject.getJSONArray("feed")

                newsList.clear()
                for (i in 0 until newsArray.length()) {
                    val newsObject = newsArray.getJSONObject(i)
                    val title = newsObject.getString("title")
                    val timePublished = newsObject.getString("time_published")
                    val bannerImage = newsObject.getString("banner_image")
                    val summary = newsObject.getString("summary")
                    val url = newsObject.getString("url")

                    val newsItem = NewsItem(title, timePublished, bannerImage, summary, url)
                    newsList.add(newsItem)
                }


                withContext(Dispatchers.Main) {
                    newsAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkFavoriteStatus() {
        val email = fAuth.currentUser?.email.toString()
        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("users").document(email)

        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val favorites = documentSnapshot.get("favorites") as? MutableList<String> ?: mutableListOf()

                val starImageView = findViewById<ImageView>(R.id.starImageView)
                if (favorites.contains(stockSymbol)) {
                    starImageView.setImageResource(R.drawable.baseline_star_24)
                } else {
                    starImageView.setImageResource(R.drawable.baseline_star_border_24)
                }
            }
        }
    }


    private fun toggleFavoriteStock() {
        val email = fAuth.currentUser?.email.toString()
        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("users").document(email)

        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val favorites = documentSnapshot.get("favorites") as? MutableList<String> ?: mutableListOf()
                val starImageView = findViewById<ImageView>(R.id.starImageView)

                if (favorites.contains(stockSymbol)) {
                    favorites.remove(stockSymbol)
                    starImageView.setImageResource(R.drawable.baseline_star_border_24)
                } else {
                    favorites.add(stockSymbol)
                    starImageView.setImageResource(R.drawable.baseline_star_24)
                }

                docRef.update("favorites", favorites)
            }
        }
    }


     private fun initializeWebSocket(stockSymbol: String?) {
        val socketUrl = "wss://ws.finnhub.io?token=${finhubApiKey}"
        val request = Request.Builder().url(socketUrl).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val subscribeData = "{\"type\":\"subscribe\",\"symbol\":\"$stockSymbol\"}"
                webSocket.send(subscribeData)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WebSocket", "WebSocket connection failure: ${t.message}")
            }
        }

         webSocket = OkHttpClient().newWebSocket(request, listener)
     }

    private fun handleWebSocketMessage(message: String) {
        try {
            val data = JSONObject(message)
            if (data.optString("type") == "trade") {
                if (stockSymbol != null) {
                    fetchAndSetData(stockSymbol, selectedFilter)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun fetchAndSetData(stockSymbol: String?, filter: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val currentDate = Calendar.getInstance()
                val endDate = currentDate.timeInMillis / 1000

                val resolution = chartConfig[filter]?.resolution
                var days = chartConfig[filter]?.days ?: 1
                val weeks = chartConfig[filter]?.weeks ?: 0
                val months = chartConfig[filter]?.months ?: 0
                val years = chartConfig[filter]?.years ?: 0

                if ((currentDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || currentDate.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) && (selectedFilter == "1D")) {
                    days = 3
                }

                val startDate = Calendar.getInstance()
                startDate.timeInMillis = endDate * 1000
                startDate.add(Calendar.DAY_OF_MONTH, -days)
                startDate.add(Calendar.WEEK_OF_YEAR, -weeks)
                startDate.add(Calendar.MONTH, -months)
                startDate.add(Calendar.YEAR, -years)
                val startTimestamp = startDate.timeInMillis / 1000

                val url = "https://finnhub.io/api/v1/stock/candle?" +
                        "symbol=${stockSymbol}&resolution=${resolution}&from=${startTimestamp}&to=${endDate}&token=${finhubApiKey}"


                val jsonString = URL(url).readText()
                val jsonObject = JSONObject(jsonString)

                val status = jsonObject.optString("s")
                if (status == "no_data") {
                    withContext(Dispatchers.Main) {
                        showToast("No data available")
                    }
                } else {
                    if (!chartInitialized || filter != previousFilter) {
                        val entries = formatData(jsonObject)
                        withContext(Dispatchers.Main) {
                            updateChartData(entries)
                        }
                        chartInitialized = true
                        previousFilter = filter
                    }

                    val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${stockSymbol}&token=${finhubApiKey}"
                    val quoteResponse = URL(quoteUrl).readText()
                    val quoteJson = JSONObject(quoteResponse)
                    val currentPrice = quoteJson.getDouble("c")
                    var priceChange: Double
                    var percentageChange: Double


                    if (selectedFilter == "1D") {
                        priceChange = quoteJson.getDouble("d")
                        percentageChange = quoteJson.getDouble("dp")

                    } else {
                        val jsonArray = jsonObject.getJSONArray("c")
                        val startPrice = jsonArray.getDouble(0)
                        priceChange = currentPrice - jsonArray.getDouble(0)
                        percentageChange = (currentPrice - startPrice) / startPrice * 100
                    }

                    val formatCurrentPrice = String.format("%.2f", currentPrice)
                    val formatPriceChange = String.format("%.2f", priceChange)
                    val formattedPercentageChange = String.format("%.2f", percentageChange)

                    withContext(Dispatchers.Main) {
                        currentPriceTextView.text = "Current Price: $formatCurrentPrice"
                        priceChangeTextView.text = "Price Change: $formatPriceChange"
                        percentageChangeTextView.text = "Percentage Change: $formattedPercentageChange%"
                    }


                    val stockDetailsURL = "https://finnhub.io/api/v1/stock/profile2?symbol=${stockSymbol}&token=${finhubApiKey}"
                    val stockDetailsResponse = URL(stockDetailsURL).readText()
                    val dataJson = JSONObject(stockDetailsResponse)

                    val marketCapitalization = dataJson.optDouble("marketCapitalization", 0.0)
                    val formattedMarketCap = convertMillionToBillion(marketCapitalization)

                    val stockDetails = """
                    Name: ${dataJson.optString("name")}
                    Country: ${dataJson.optString("country")}
                    Currency: ${dataJson.optString("currency")}
                    Exchange: ${dataJson.optString("exchange")}
                    IPO Date: ${dataJson.optString("ipo")}
                    Market Capitalization: ${formattedMarketCap}
                    Industry: ${dataJson.optString("finnhubIndustry")}
                """.trimIndent()

                    withContext(Dispatchers.Main) {
                        stockDetailsTextView.text = stockDetails
                    }


                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error fetching data")
                }
                e.printStackTrace()
            }
        }
    }

    private fun convertMillionToBillion(number: Double): String {
        return String.format("%.2fB", number / 1000)
    }


    private fun formatData(data: JSONObject): List<Entry> {
        val timestamps = data.getJSONArray("t")
        val prices = data.getJSONArray("c")

        val entries = mutableListOf<Entry>()

        for (i in 0 until timestamps.length()) {
            val timestamp = timestamps.getLong(i)
            val price = prices.getDouble(i).toFloat()
            entries.add(Entry(timestamp.toFloat(), price))
        }
        return entries
    }

    private fun updateChartData(entries: List<Entry>) {
        val lineDataSet = LineDataSet(entries, "Stock Price")
        lineDataSet.color = resources.getColor(R.color.black)
        lineDataSet.valueTextColor = resources.getColor(android.R.color.black)

        val lineData = LineData(lineDataSet)
        lineChart.data = lineData
        lineChart.xAxis.labelRotationAngle = -45f
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.valueFormatter = DateValueFormatter()
        lineChart.invalidate()
    }

    private fun checkIfMarketIsOpen(): Boolean {
        val now = Calendar.getInstance()
        val currentHourSGT = now.get(Calendar.HOUR) + 1
        val currentMonth = now.get(Calendar.MONTH) + 1

        var isMarketOpen = false

        if ((currentHourSGT <= 4 || currentHourSGT >= 21.5) && (currentMonth >= 4 && currentMonth <= 9)) {
            isMarketOpen = true
        } else if ((currentHourSGT <= 5 || currentHourSGT >= 22.5) && (currentMonth >= 10 || currentMonth <= 3)) {
            isMarketOpen = true
        }

        return isMarketOpen
    }

    inner class DateValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return dateFormatter.format(Date(value.toLong() * 1000))
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this@DetailsActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this@DetailsActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}