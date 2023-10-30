package securitytrading.com

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class SummaryActivity : AppCompatActivity() {

    var stockSymbol = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        stockSymbol = intent.getStringExtra("stockSymbol").toString()
        val action = intent.getStringExtra("action")
        val tradeType = intent.getStringExtra("tradeType")
        val totalPrice = intent.getDoubleExtra("totalPrice", 0.00)
        val fundsLeft = intent.getDoubleExtra("fundsLeft", 0.00)
        val unitsBoughtOrSold = intent.getIntExtra("unitsBoughtOrSold", 0)
        val newUnitsHeld = intent.getIntExtra("newUnitsHeld", 0)

        val okButton = findViewById<Button>(R.id.okButton)

        okButton.setOnClickListener {
            val intent = Intent(this@SummaryActivity, DetailsActivity::class.java)
            intent.putExtra("symbol", stockSymbol)
            startActivity(intent)
            finish()
        }

        val stockSymbolTextView = findViewById<TextView>(R.id.stockSymbolTextView)
        val actionTextView = findViewById<TextView>(R.id.actionTextView)
        val tradeTypeTextView = findViewById<TextView>(R.id.tradeTypeTextView)
        val totalPriceTextView = findViewById<TextView>(R.id.totalPriceTextView)
        val fundsLeftTextView = findViewById<TextView>(R.id.fundsLeftTextView)
        val unitsBoughtOrSoldTextView = findViewById<TextView>(R.id.unitsBoughtOrSoldTextView)
        val newUnitsHeldTextView = findViewById<TextView>(R.id.newUnitsHeldTextView)

        stockSymbolTextView.text = "Stock Symbol: $stockSymbol"
        actionTextView.text = "Action: $action"
        tradeTypeTextView.text = "Trade type: $tradeType"
        totalPriceTextView.text = "Total Price: $totalPrice"
        fundsLeftTextView.text = "Funds Left: $fundsLeft"
        unitsBoughtOrSoldTextView.text = "Units Bought/Sold: $unitsBoughtOrSold"
        newUnitsHeldTextView.text = "New Units Held: $newUnitsHeld"
    }

    override fun onBackPressed() {
        val intent = Intent(this@SummaryActivity, DetailsActivity::class.java)
        intent.putExtra("symbol", stockSymbol)
        startActivity(intent)
        finish()
    }

}