import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import androidx.recyclerview.widget.RecyclerView
import securitytrading.com.DetailsActivity.NewsItem
import securitytrading.com.R

class NewsAdapter(private val newsList: List<NewsItem>) :
    RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.list_item_news, parent, false)
        return NewsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val newsItem = newsList[position]
        holder.newsTitle.text = newsItem.title
        holder.newsTimePublished.text = formatDate(newsItem.timePublished)
        holder.newsSummary.text = newsItem.summary
        holder.newsUrl.text = newsItem.url

        Picasso.get().load(newsItem.bannerImage).into(holder.newsBannerImage)

        holder.newsUrl.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newsItem.url))
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return newsList.size
    }

    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val newsTitle: TextView = itemView.findViewById(R.id.newsTitleTextView)
        val newsTimePublished: TextView = itemView.findViewById(R.id.newsTimePublishedTextView)
        val newsSummary: TextView = itemView.findViewById(R.id.newsSummaryTextView)
        val newsBannerImage: ImageView = itemView.findViewById(R.id.newsBannerImageView)
        val newsUrl: TextView = itemView.findViewById(R.id.newsUrlTextView)
    }

    private fun formatDate(time: String): String {
        val year = time.substring(0, 4)
        val month = time.substring(4, 6)
        val day = time.substring(6, 8)

        return "$day-$month-$year"
    }
}
