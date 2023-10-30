package securitytrading.com

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

 class NewsFragment : Fragment() {

    data class NewsItem(
        val title: String,
        val timePublished: String,
        val bannerImage: String,
        val summary: String,
        val url: String
    )

    private lateinit var stockSymbol: String
    private val newsList: MutableList<NewsItem> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.newsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        val searchBar = activity?.findViewById<EditText>(R.id.searchBar)
        searchBar?.setOnClickListener {
            activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }

        stockSymbol = arguments?.getString(ARG_STOCK_SYMBOL) ?: ""

        if (!stockSymbol.isEmpty()) {
            fetchNewsData(stockSymbol)
        }

        return view
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val ARG_STOCK_SYMBOL = "stock_symbol"

        fun newInstance(stockSymbol: String) =
            NewsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STOCK_SYMBOL, stockSymbol)
                }
            }
    }
}
