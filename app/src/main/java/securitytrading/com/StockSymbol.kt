package securitytrading.com

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class StockSymbol(val description: String, val displaySymbol: String)

class StockSymbolAdapter(private val stockSymbols: List<StockSymbol>) :
    RecyclerView.Adapter<StockSymbolAdapter.ViewHolder>() {

    private var itemClickListener: ((StockSymbol) -> Unit)? = null

    fun setOnItemClickListener(listener: (StockSymbol) -> Unit) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_stock_symbol, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stockSymbol = stockSymbols[position]
        holder.symbolTextView.text = "${stockSymbol.description} (${stockSymbol.displaySymbol})"

        holder.itemView.setOnClickListener {
            itemClickListener?.invoke(stockSymbol)
        }
    }

    override fun getItemCount(): Int {
        return stockSymbols.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val symbolTextView: TextView = itemView.findViewById(R.id.symbolTextView)
    }
}