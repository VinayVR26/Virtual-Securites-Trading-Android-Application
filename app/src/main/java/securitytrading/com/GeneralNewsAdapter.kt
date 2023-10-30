import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import securitytrading.com.HomeFragment.NewsResponse
import securitytrading.com.R
import java.text.SimpleDateFormat
import java.util.*

class GeneralNewsAdapter(private val newsList: List<NewsResponse>) :
    RecyclerView.Adapter<GeneralNewsAdapter.NewsViewHolder>() {

    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.newsTitleTextView)
        val timePublishedTextView: TextView = itemView.findViewById(R.id.newsTimePublishedTextView)
        val bannerImageView: ImageView = itemView.findViewById(R.id.newsBannerImageView)
        val summaryTextView: TextView = itemView.findViewById(R.id.newsSummaryTextView)
        val urlTextView: TextView = itemView.findViewById(R.id.newsUrlTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_news, parent, false)
        return NewsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = newsList[position]
        holder.titleTextView.text = news.headline
        val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            .format(Date(news.datetime * 1000))
        holder.timePublishedTextView.text = formattedDate
        holder.summaryTextView.text = news.summary
        holder.urlTextView.text = news.url

        Picasso.get().load(news.image).into(holder.bannerImageView)
    }

    override fun getItemCount() = newsList.size
}
