package securitytrading.com

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime


class PortfolioFragment : Fragment() {
    val fAuth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"
    var stockSymbol = ""
    var investedAmount = 0.00f

    var investedStocks: MutableList<String> = mutableListOf()
    var existingStocks: List<HashMap<String, Any>> = emptyList()
    var existingWaitStocks: List<HashMap<String, Any>> = emptyList()
    var reversedStocks: List<HashMap<String, Any>> = emptyList()
    var stockData: HashMap<String, Any>? = null
    var latestUnits = 0

    var fundsForTrading = 0.00f

    var stockNPrices: MutableList<Pair<String, Int>> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_portfolio, container, false)

        val pieChart = view.findViewById(R.id.pieChart) as PieChart
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)

        val entries = ArrayList<PieEntry>()

        fun getCurrentPrice(stockNPrices: MutableList<Pair<String, Int>>) {
            var totalValue = 0.00f
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    for ((ticker, units) in stockNPrices) {
                        val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${ticker}&token=${finhubApiKey}"
                        val quoteResponse = URL(quoteUrl).readText()
                        val quoteJson = JSONObject(quoteResponse)
                        val currentPrice = quoteJson.getDouble("c").toFloat()
                        var totalValue = currentPrice * units

                        var percentage = (totalValue / investedAmount) * 100
                        entries.add(PieEntry(percentage, ticker))

                        withContext(Dispatchers.Main) {

                            val dataSet = PieDataSet(entries, "")
                            dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
                            val data = PieData(dataSet)
                            pieChart.data = data

                            val legend = pieChart.legend
                            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                            legend.orientation = Legend.LegendOrientation.HORIZONTAL
                            legend.setDrawInside(false)

                            legend.xOffset = 0f
                            legend.yOffset = 80f

                            pieChart.invalidate()
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
                        fundsForTrading = documentSnapshot.get("fundsForTrading").toString().toFloat()
                        investedStocks = documentSnapshot.get("investedStocks") as? MutableList<String> ?: mutableListOf()
                        investedAmount = documentSnapshot.get("totalCashFlow").toString().toFloat()
                        existingStocks = documentSnapshot.get("stocks") as? List<HashMap<String, Any>>?: emptyList()
                        existingWaitStocks = documentSnapshot.get("waitStocks") as? List<HashMap<String, Any>>?: emptyList()
                        if (existingStocks != null) {
                            reversedStocks = existingStocks.asReversed()

                            for (aStock in investedStocks) {
                                stockData = reversedStocks.find { stock -> stock["stockSymbol"] == aStock }
                                latestUnits = stockData?.get("latestUnits")?.toString()?.toInt() ?: 0

                                stockSymbol = aStock
                                stockNPrices += Pair(stockSymbol, latestUnits)
                            }
                            getCurrentPrice(stockNPrices)
                        }
                    }
                }
            }

        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                updateTCF()
                delay(10000)
                if (existingWaitStocks.size > 0) {
                    fillLimitOrders()
                    delay(10000)
                }
            }
        }

        return view
    }

    @SuppressLint("SuspiciousIndentation")
    suspend fun updateTCF() {
        var tickerTCF = ""
            var existingMutableReversedStocks = reversedStocks.toMutableList()
            var uniqueStocks = mutableListOf<Map<String, Any?>>()

            for (reversedStock in existingMutableReversedStocks) {
                val tickerTCF = reversedStock["stockSymbol"].toString()
                val stockExists = uniqueStocks.any { it["stockSymbol"] == tickerTCF }

                if (!stockExists) {
                    val newStockData = mapOf(
                        "stockSymbol" to tickerTCF,
                        "unitsInPossession" to reversedStock["latestUnits"],
                        "averagePriceSpent" to reversedStock["averagePriceSpent"]
                    )
                    uniqueStocks.add(newStockData)
                }
            }

            var valueToAdd = 0.00f
            var valueFromTrading = 0.00f

            for (data in uniqueStocks) {
                tickerTCF = data["stockSymbol"].toString()
                val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${tickerTCF}&token=${finhubApiKey}"

                try {
                    val quoteResponse = withContext(Dispatchers.IO) {
                        URL(quoteUrl).openStream().bufferedReader().use { it.readText() }
                    }

                    val quoteJson = JSONObject(quoteResponse)
                    val stockPrice = quoteJson.getDouble("c").toFloat()
                    valueToAdd = data["unitsInPossession"].toString().toInt() * stockPrice
                    valueFromTrading += valueToAdd
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val updatedTotalCashFlow = fundsForTrading + valueFromTrading

            var totalCashFlowUpdate = HashMap<String, Any>()
            totalCashFlowUpdate = hashMapOf(
                "totalCashFlow" to updatedTotalCashFlow.toFloat()
            )

            val userEmail = fAuth.currentUser?.email
            if (userEmail != null) {
                val userDocument = db.collection("users").document(userEmail)
                userDocument.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        userDocument.update(totalCashFlowUpdate).addOnCompleteListener { updateTask ->
                        }
                    }
                }
            }
    }

    suspend fun fillLimitOrders() {
        var tickerLimit = ""
        var updateLimitData = HashMap<String, Any>()
        val userEmail = fAuth.currentUser?.email
        var existingMutableStocks = existingStocks.toMutableList()
        var existingMutableWaitStocks = existingWaitStocks.toMutableList()
        for (stock in existingMutableWaitStocks) {

            tickerLimit = stock["stockSymbol"].toString()
            val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${tickerLimit}&token=${finhubApiKey}"
            try {
                val quoteResponse = withContext(Dispatchers.IO) {
                    URL(quoteUrl).readText()
                }

                val quoteJson = JSONObject(quoteResponse)
                val currentPrice = quoteJson.getDouble("c").toFloat()
                if (stock["action"] == "BUY" && currentPrice != null && stock["limitPrice"] != null && currentPrice <= stock["limitPrice"].toString().toFloat()) {
                    val totalPrice = currentPrice * stock["unitsBoughtInTransaction"].toString().toInt()
                    if (fundsForTrading >= totalPrice) {
                        val fundsLeft = fundsForTrading - totalPrice
                        stock.remove("limitPrice")
                        stock["latestFundsForTrading"] = fundsLeft
                        stock["order"] = "FILLED"
                        stock["pricePerUnit"] = currentPrice
                        stock["expenditureOfTransaction"] = totalPrice

                        val findStock = reversedStocks.find { aStock -> aStock["stockSymbol"] == stock["stockSymbol"] }
                        if (findStock != null) {
                            stock["latestUnits"] = findStock["latestUnits"].toString().toInt() + stock["unitsBoughtInTransaction"].toString().toInt()
                            stock["totalExpenditureOnStock"] = totalPrice + findStock["totalExpenditureOnStock"].toString().toFloat()
                            stock["totalReturnsFromStock"] = findStock["totalReturnsFromStock"].toString().toFloat()
                            stock["totalUnitsBought"] = stock["unitsBoughtInTransaction"].toString().toInt() + findStock["totalUnitsBought"].toString().toInt()
                            stock["totalUnitsSold"] = findStock["totalUnitsSold"].toString().toFloat()
                            stock["averagePriceSpent"] = stock["totalExpenditureOnStock"].toString().toFloat() / stock["totalUnitsBought"].toString().toInt()
                        } else {
                            stock["latestUnits"] = stock["unitsBoughtInTransaction"].toString().toInt()
                            stock["totalExpenditureOnStock"] = totalPrice
                            stock["totalReturnsFromStock"] = 0
                            stock["totalUnitsBought"] = stock["unitsBoughtInTransaction"].toString().toInt()
                            stock["totalUnitsSold"] = 0
                            stock["averagePriceSpent"] = stock["totalExpenditureOnStock"].toString().toFloat() / stock["totalUnitsBought"].toString().toInt()
                        }

                        val now = LocalDateTime.now()
                        stock["date"] = now
                        existingMutableStocks.add(stock)

                        if (!investedStocks.contains(stock["stockSymbol"])) {
                            investedStocks.add(stock["stockSymbol"].toString())
                        }

                        existingMutableWaitStocks.removeAll { it["id"] == stock["id"] }

                        updateLimitData = hashMapOf(
                            "fundsForTrading" to fundsLeft.toFloat(),
                            "stocks" to existingMutableStocks,
                            "waitStocks" to existingMutableWaitStocks,
                            "investedStocks" to investedStocks
                        )


                        if (userEmail != null) {
                            val userDocument = db.collection("users").document(userEmail)
                            val task = userDocument.get()
                            if (task.isSuccessful) {
                                userDocument.update(updateLimitData)
                            }
                        }
                    }
                } else if (stock["action"] == "SELL" && currentPrice != null && currentPrice >= stock["limitPrice"].toString().toFloat()) {
                    val findSellStock = reversedStocks.find { bStock -> bStock["stockSymbol"] == stock["stockSymbol"] }
                    if (findSellStock != null && findSellStock["latestUnits"].toString().toInt() >= stock["unitsSoldInTransaction"].toString().toInt()) {
                        val totalPrice = currentPrice * stock["unitsSoldInTransaction"].toString().toInt()
                        val fundsLeft = fundsForTrading + totalPrice
                        stock.remove("limitPrice")
                        stock["latestFundForTrading"] = fundsLeft
                        stock["order"] = "FILLED"
                        stock["pricePerUnit"] = currentPrice
                        stock["returnsFromTransaction"] = totalPrice

                        stock["latestUnits"] = findSellStock["latestUnits"].toString().toInt() - stock["unitsSoldInTransaction"].toString().toInt()
                        stock["totalExpenditureOnStock"] = findSellStock["totalExpenditureOnStock"].toString().toFloat()
                        stock["totalReturnsFromStock"] = totalPrice + findSellStock["totalReturnsFromStock"].toString().toFloat()
                        stock["totalUnitsBought"] = findSellStock["totalUnitsBought"].toString().toFloat()
                        stock["totalUnitsSold"] = stock["unitsSoldInTransaction"].toString().toInt() + findSellStock["totalUnitsSold"].toString().toInt()
                        stock["averagePriceSpent"] = findSellStock["averagePriceSpent"].toString().toFloat()

                        val now = LocalDateTime.now()
                        stock["date"] = now
                        existingMutableStocks.add(stock)

                        if (stock["latestUnits"] == 0) {
                            investedStocks.remove(stock["stockSymbol"])
                        }

                        updateLimitData = hashMapOf(
                            "fundsForTrading" to fundsLeft.toFloat(),
                            "stocks" to existingMutableStocks,
                            "waitStocks" to existingMutableWaitStocks
                        )

                        if (userEmail != null) {
                            val userDocument = db.collection("users").document(userEmail)
                            val task = userDocument.get()
                            if (task.isSuccessful) {
                                userDocument.update(updateLimitData)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}