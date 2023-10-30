package securitytrading.com

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import securitytrading.com.databinding.ActivityTransactionBinding
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TimeZone

class TransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    var stockSymbol = ""
    var unitsToDisplay = 0
    var currentUnits = 0
    var setPrice = 0.00f
    var totalPrice = 0.00
    var initialFunds = 0.00
    var fundsLeft = 0.00
    var quantity = 0
    var selectedAction = "BUY"
    var selectedMarketType = "MARKET"
    var selectedTif = "DAY"
    var username = ""
    var fundsForTrading = 0.00
    var averagePriceSpent = 0.00

    var expenditureOfTransaction = 0.00
    var order = "FILLED"
    var returnsFromTransaction = 0.00
    var totalExpenditureOnStock = 0.00
    var totalReturnsFromStock = 0.00
    var totalUnitsBought = 0
    var totalUnitsSold = 0
    var unitsBoughtInTransaction = 0
    var unitsSoldInTransaction = 0

    var investedStocks: MutableList<String> = mutableListOf()
    var existingStocks: List<HashMap<String, Any>> = emptyList()
    var existingWaitStocks: List<HashMap<String, Any>> = emptyList()
    var reversedStocks: List<HashMap<String, Any>> = emptyList()
    var stockData: HashMap<String, Any>? = null

    var transactionData: HashMap<String, Any> = hashMapOf(
        "stockSymbol" to stockSymbol,
        "pricePerUnit" to setPrice.toFloat(),
        "latestUnits" to currentUnits ,
        "action" to selectedAction,
        "tradeType" to selectedMarketType,
        "tif" to selectedTif,
        "account" to username,
        "latestFundsForTrading" to fundsLeft.toFloat(),
        "averagePriceSpent" to averagePriceSpent.toFloat(),
        "expenditureOfTransaction" to expenditureOfTransaction.toFloat(),
        "order" to order,
        "returnsFromTransaction" to returnsFromTransaction.toFloat(),
        "totalExpenditureOnStock" to totalExpenditureOnStock.toFloat(),
        "totalReturnsFromStock" to totalReturnsFromStock.toFloat(),
        "totalUnitsBought" to totalUnitsBought,
        "totalUnitsSold" to totalUnitsSold,
        "unitsBoughtInTransaction" to unitsBoughtInTransaction,
        "unitsSoldInTransaction" to unitsSoldInTransaction
    )

    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"

    private lateinit var webSocket: WebSocket
    private val priceUpdateThrottleInterval = 4000L
    private var lastPriceUpdateTimestamp = 0L

    private lateinit var currentUnitsTextView: TextView
    private lateinit var newUnitsTextView: TextView
    private lateinit var quantityEditText: EditText
    private lateinit var limitPriceEditText: EditText
    private lateinit var totalPriceTextView: TextView
    private lateinit var currentFundsTextView: TextView
    private lateinit var newFundsTextView: TextView

    private val handler = Handler()
    private val updateInterval = 10000
    var currentPriceTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val stockSymbolTextView = binding.stockSymbolTextView
        val actionTypeSpinner = binding.actionTypeSpinner
        val marketTypeSpinner = binding.marketTypeSpinner
        val tifSpinner = binding.tifSpinner
        val usernameTextView = binding.usernameTextView
        val tradeButton = binding.tradeButton
        tradeButton.visibility = if (isMarketOpen()) View.VISIBLE else View.GONE


        currentPriceTextView = binding.unitPriceTextView


        stockSymbol = intent.getStringExtra("stockSymbol").toString()
        stockSymbolTextView.text = stockSymbol
        setPrice = intent.getStringExtra("unitPrice")?.toFloat() ?: 0.00f

        transactionData["stockSymbol"] = stockSymbol


        currentUnitsTextView = findViewById(R.id.currentUnitsTextView)
        newUnitsTextView = findViewById(R.id.newUnitsTextView)
        quantityEditText = findViewById(R.id.quantityEditText)
        limitPriceEditText = findViewById(R.id.limitPriceEditText)
        totalPriceTextView = findViewById(R.id.totalPriceTextView)
        currentFundsTextView = findViewById(R.id.currentFundsTextView)
        newFundsTextView = findViewById(R.id.newFundsTextView)

        limitPriceEditText.visibility = View.GONE
        limitPriceEditText.setText(setPrice.toString())

        marketTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMarketType = parent?.getItemAtPosition(position).toString()
                updateDisplayData(selectedAction)

                if (selectedMarketType == "LIMIT") {
                    limitPriceEditText.visibility = View.VISIBLE
                } else {
                    limitPriceEditText.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }


        quantityEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && quantity == 0) {
                quantityEditText.text.clear()
            }
        }

        quantityEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateDisplayData(selectedAction)
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        actionTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedAction = parent?.getItemAtPosition(position).toString()
                updateDisplayData(selectedAction)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }


        initializeWebSocket(stockSymbol)


        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userEmail = currentUser.email
            if (userEmail != null) {
                db.collection("users").document(userEmail).get().addOnSuccessListener { documentSnapshot: DocumentSnapshot? ->
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            username = documentSnapshot.getString("username").toString()
                            transactionData["account"] = username
                            usernameTextView.text = "Username: $username"
                            fundsForTrading = documentSnapshot.getDouble("fundsForTrading")?.toDouble() ?:0.0
                            initialFunds = fundsForTrading
                            currentFundsTextView.text = "Current funds for trading: $fundsForTrading"
                            newFundsTextView.text = "Funds left for trading: $fundsForTrading"
                            investedStocks = documentSnapshot.get("investedStocks") as? MutableList<String> ?: mutableListOf()

                            existingStocks = documentSnapshot.get("stocks") as? List<HashMap<String, Any>>?: emptyList()
                            existingWaitStocks = documentSnapshot.get("waitStocks") as? List<HashMap<String, Any>>?: emptyList()
                            if (existingStocks != null) {
                                reversedStocks = existingStocks.asReversed()
                                stockData = reversedStocks.find { stock -> stock["stockSymbol"] == stockSymbol }

                                if (stockData != null) {
                                    val latestUnits = stockData?.get("latestUnits")?.toString()?.toInt() ?: 0
                                    currentUnits = latestUnits
                                    currentUnitsTextView.text = "Current units of ${stockSymbol} held: ${currentUnits}"
                                    newUnitsTextView.text = "New units of ${stockSymbol} held: ${currentUnits}"

                                } else {
                                    currentUnitsTextView.text = "Current units of ${stockSymbol} held: 0"
                                    newUnitsTextView.text = "New units of ${stockSymbol} held: 0"
                                }

                                transactionData["latestUnits"] = currentUnits
                            }
                        }
                }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error fetching username: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }


        val actionTypes = arrayOf("BUY", "SELL")
        val actionTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actionTypes)
        actionTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        actionTypeSpinner.adapter = actionTypeAdapter

        val marketTypes = arrayOf("MARKET", "LIMIT")
        val marketTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, marketTypes)
        marketTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        marketTypeSpinner.adapter = marketTypeAdapter

        val tifTypes = arrayOf("DAY", "GTC")
        val tifAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tifTypes)
        tifAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        tifSpinner.adapter = tifAdapter

        tradeButton.setOnClickListener {
            quantity = quantityEditText.text.toString().toInt()
            selectedAction = actionTypeSpinner.selectedItem.toString()
            selectedMarketType = marketTypeSpinner.selectedItem.toString()
            selectedTif = tifSpinner.selectedItem.toString()

            val confirmationMessage =
                "You are about to $selectedAction $quantity shares of $stockSymbol at $selectedMarketType price with TIF: $selectedTif. Account: $username"
            Toast.makeText(this, confirmationMessage, Toast.LENGTH_LONG).show()


            if (selectedAction == "BUY" && selectedMarketType == "MARKET") {
                transactionData["unitsBoughtInTransaction"] = quantity
                transactionData["unitsSoldInTransaction"] = 0
                transactionData["expenditureOfTransaction"] = totalPrice
                transactionData["returnsFromTransaction"] = 0
                transactionData["latestUnits"] = currentUnits + quantity
                transactionData["tradeType"] = selectedMarketType
            } else if (selectedAction == "SELL" && selectedMarketType == "MARKET") {
                transactionData["unitsBoughtInTransaction"] = 0
                transactionData["unitsSoldInTransaction"] = quantity
                transactionData["expenditureOfTransaction"] = 0
                transactionData["returnsFromTransaction"] = totalPrice
                transactionData["latestUnits"] = currentUnits - quantity
                transactionData["tradeType"] = selectedMarketType
            } else if (selectedAction == "BUY" && selectedMarketType == "LIMIT") {
                transactionData["unitsBoughtInTransaction"] = quantity
                transactionData["unitsSoldInTransaction"] = 0
                transactionData["expenditureOfTransaction"] = 0
                transactionData["returnsFromTransaction"] = 0
                transactionData["latestUnits"] = currentUnits + quantity
                transactionData["tradeType"] = selectedMarketType
            } else if (selectedAction == "SELL" && selectedMarketType == "LIMIT") {
                transactionData["unitsBoughtInTransaction"] = 0
                transactionData["unitsSoldInTransaction"] = quantity
                transactionData["expenditureOfTransaction"] = 0
                transactionData["returnsFromTransaction"] = 0
                transactionData["latestUnits"] = currentUnits - quantity
                transactionData["tradeType"] = selectedMarketType
            }

            val stockDataNotNull = stockData
            if (stockDataNotNull == null) {
                if (selectedAction == "BUY" && selectedMarketType == "MARKET") {
                    transactionData["totalExpenditureOnStock"] = totalPrice
                    transactionData["totalReturnsFromStock"] = 0
                    transactionData["totalUnitsBought"] = quantity
                    transactionData["totalUnitsSold"] = 0
                    transactionData["averagePriceSpent"] = (totalPrice / quantity).toFloat()
                }
            } else {
                if (selectedAction == "BUY" && selectedMarketType == "MARKET") {
                    transactionData["totalExpenditureOnStock"] = (totalPrice + (stockDataNotNull["totalExpenditureOnStock"] as Double))
                    transactionData["totalReturnsFromStock"] = stockDataNotNull["totalReturnsFromStock"] as Long
                    transactionData["totalUnitsBought"] = quantity + (stockDataNotNull["totalUnitsBought"] as Long)
                    transactionData["totalUnitsSold"] = stockDataNotNull["totalUnitsSold"] as Long
                    transactionData["averagePriceSpent"] = (transactionData["totalExpenditureOnStock"] as Double / transactionData["totalUnitsBought"] as Long)
                } else if (selectedAction == "SELL" && selectedMarketType == "MARKET") {
                    transactionData["totalExpenditureOnStock"] = stockDataNotNull["totalExpenditureOnStock"] as Long
                    transactionData["totalReturnsFromStock"] = totalPrice + (stockDataNotNull["totalReturnsFromStock"] as Long)
                    transactionData["totalUnitsBought"] = stockDataNotNull["totalUnitsBought"] as Long
                    transactionData["totalUnitsSold"] = quantity + (stockDataNotNull["totalUnitsSold"] as Long)
                    transactionData["averagePriceSpent"] = stockDataNotNull["averagePriceSpent"] as Long
                }
            }

            transactionData["latestFundsForTrading"]  = fundsLeft.toFloat()

            var updateData = HashMap<String, Any>()
            val now = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss")
            val dateTime = now.format(formatter)


            if (selectedMarketType == "MARKET") {
                transactionData["pricePerUnit"] = setPrice
                transactionData["order"] = "FILLED"
                transactionData["date"] = dateTime
                val newStocksList = existingStocks.toMutableList()
                newStocksList.add(transactionData)

                if (!investedStocks.contains(stockSymbol)) {
                    investedStocks.add(stockSymbol)
                }


                updateData = hashMapOf(
                    "fundsForTrading" to fundsLeft.toFloat(),
                    "stocks" to newStocksList,
                    "investedStocks" to investedStocks
                )

            } else {
                val limitPrice = limitPriceEditText.text.toString().toDoubleOrNull()
                if (limitPrice != null) {
                    transactionData["pricePerUnit"] = setPrice
                    transactionData["order"] = "WAITING"
                    transactionData["date"] = dateTime
                    transactionData["limitPrice"] = limitPrice
                    transactionData["id"] = dateTime
                    val newWaitStocksList = existingWaitStocks.toMutableList()
                    newWaitStocksList.add(transactionData)

                    updateData = hashMapOf(
                        "waitStocks" to newWaitStocksList
                    )
                }
            }

            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userEmail = currentUser.email
                if (userEmail != null) {
                    val userDocument = db.collection("users").document(userEmail)
                    userDocument.get().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            userDocument.update(updateData).addOnCompleteListener { updateTask ->
                            }
                        } else {
                            Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }



            val intent = Intent(this@TransactionActivity, SummaryActivity::class.java)
            intent.putExtra("stockSymbol", stockSymbol)
            intent.putExtra("action", selectedAction)
            intent.putExtra("tradeType", selectedMarketType)
            intent.putExtra("totalPrice", totalPrice)
            intent.putExtra("fundsLeft", fundsLeft)
            intent.putExtra("unitsBoughtOrSold", quantity)
            intent.putExtra("newUnitsHeld", unitsToDisplay)

            startActivity(intent)
            finish()
        }
    }







    private fun updateDisplayData(selectedAction: String) {
        unitsToDisplay = 0
        newUnitsTextView.text = "New units of ${stockSymbol} held: ${currentUnits}"
        quantityEditText.error = null
        quantity = quantityEditText.text.toString().toIntOrNull() ?: 0
        fundsLeft = initialFunds
        if (quantity != null && quantity != 0) {
            totalPrice = quantity * setPrice.toDouble()
            if (selectedAction == "BUY") {
                if (totalPrice <= fundsLeft) {
                    fundsLeft = fundsForTrading - totalPrice
                    totalPriceTextView.text = "Total price: $totalPrice"
                    newFundsTextView.text = "Funds left for trading: $fundsLeft"
                    unitsToDisplay = currentUnits + quantity
                    newUnitsTextView.text = "New units of ${stockSymbol} held: ${unitsToDisplay}"
                } else {
                    quantityEditText.error = "Insufficient funds for this transaction"
                    totalPriceTextView.text = "Total price: 0.00"
                    newFundsTextView.text = "Funds left for trading: $initialFunds"
                }
            } else {
                if (currentUnits >= quantity) {
                    fundsLeft = fundsForTrading + totalPrice
                    totalPriceTextView.text = "Total price: $totalPrice"
                    newFundsTextView.text = "Funds left for trading: $fundsLeft"
                    unitsToDisplay = currentUnits - quantity
                    newUnitsTextView.text = "New units of ${stockSymbol} held: ${unitsToDisplay}"
                } else {
                    quantityEditText.error = "Cannot sell more units than you have"
                    totalPriceTextView.text = "Total price: 0.00"
                    newFundsTextView.text = "Funds left for trading: $initialFunds"
                }
            }
        } else {
            totalPriceTextView.text = "Total price: 0.00"
            newFundsTextView.text = "Funds left for trading: $initialFunds"
        }
    }

    private fun initializeWebSocket(stockSymbol: String?) {
        val socketUrl = "wss://ws.finnhub.io?token=${finhubApiKey}"
        val request = Request.Builder().url(socketUrl).build()
        if (!isMarketOpen()) {
            fetchAndSetPrice(stockSymbol)
        } else {
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

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e("WebSocket", "WebSocket connection failure: ${t.message}")
                    if (response != null) {
                        Log.e("WebSocket", "Response code: ${response.code}")
                        Log.e("WebSocket", "Response message: ${response.message}")
                    }
                }
            }

            webSocket = OkHttpClient().newWebSocket(request, listener)
        }
    }

    private fun handleWebSocketMessage(message: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val elapsedTimeSinceLastUpdate = currentTime - lastPriceUpdateTimestamp
            if (elapsedTimeSinceLastUpdate >= priceUpdateThrottleInterval) {
                lastPriceUpdateTimestamp = currentTime

                val data = JSONObject(message)
                if (data.optString("type") == "trade") {
                    if (stockSymbol != null) {
                        fetchAndSetPrice(stockSymbol)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchAndSetPrice(stockSymbol: String?) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val quoteUrl = "https://finnhub.io/api/v1/quote?symbol=${stockSymbol}&token=${finhubApiKey}"
                val quoteResponse = URL(quoteUrl).readText()
                val quoteJson = JSONObject(quoteResponse)
                val currentPrice = quoteJson.getDouble("c")

                val formatCurrentPrice = String.format("%.2f", currentPrice)

                setPrice = formatCurrentPrice.toFloat()

                withContext(Dispatchers.Main) {
                    currentPriceTextView?.text = "Current Price: $setPrice"
                }


            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                }
                e.printStackTrace()
            }
        }
    }


    private fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Singapore"))

        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        return when {
            currentDayOfWeek in Calendar.MONDAY..Calendar.THURSDAY -> {
                (currentHour > 21 || (currentHour == 21 && currentMinute >= 30)) || currentHour < 4
            }

            currentDayOfWeek == Calendar.FRIDAY -> {
                (currentHour > 21 || (currentHour == 21 && currentMinute >= 30)) || currentDayOfWeek == Calendar.SATURDAY
            }

            else -> false
        }
    }
}