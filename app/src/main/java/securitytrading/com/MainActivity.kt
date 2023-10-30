package securitytrading.com

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import securitytrading.com.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var fragmentManager : FragmentManager
    private lateinit var binding: ActivityMainBinding
    val PortfolioFragment = PortfolioFragment()

    private var selectedSideItemId: Int = -1
    private var selectedBottomItemId: Int = R.id.bottom_home

    val fAuth = FirebaseAuth.getInstance()
    val fStore = FirebaseFirestore.getInstance()
    val fStorage = FirebaseStorage.getInstance()

    private var searchResultsPopup: PopupWindow? = null

    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchResultsAdapter: StockSymbolAdapter
    private val stockSymbolsList: MutableList<StockSymbol> = mutableListOf()
    private lateinit var searchBar: EditText

    private val searchHandler = Handler(Looper.getMainLooper())
    private val SEARCH_DELAY = 1
    private var isDropdownDismissed = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.open, R.string.close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationDrawer.setNavigationItemSelectedListener(this)

        binding.bottomNavigation.background = null
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> openFragment(HomeFragment())
                R.id.bottom_watchlist -> openFragment(WatchlistFragment())
                R.id.bottom_portfolio -> openFragment(PortfolioFragment())
                R.id.bottom_discover -> openFragment(DiscoverFragment())
            }

            selectedBottomItemId = item.itemId
            println(selectedBottomItemId)

            val previousSideItem = binding.navigationDrawer.menu.findItem(selectedSideItemId)
            println(previousSideItem)

            previousSideItem?.isChecked = false
            true
        }


        fragmentManager = supportFragmentManager
        openFragment(HomeFragment())

        val email = fAuth.currentUser?.email.toString()
        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("users").document(email)

        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val imageUrl = documentSnapshot.getString("imageUrl")
                val username = documentSnapshot.getString("username")

                if (!imageUrl.isNullOrBlank()) {

                    val navHeader = binding.navigationDrawer.getHeaderView(0)
                    val profileImageView = navHeader.findViewById<ImageView>(R.id.profileImageView)
                    Picasso.get().load(imageUrl).into(profileImageView)


                    val profileUsername = navHeader.findViewById<TextView>(R.id.profileUsername)
                    profileUsername.text = username
                }
            }
        }

        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        searchResultsAdapter = StockSymbolAdapter(stockSymbolsList)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchResultsAdapter

        searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used in this case
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchHandler.removeCallbacksAndMessages(null)

                searchHandler.postDelayed({
                    val query = s.toString()
                    if (!query.isEmpty()) {
                        performSymbolSearch(query)
                    } else {
                        searchResultsPopup?.dismiss()
                    }
                }, SEARCH_DELAY.toLong())
            }

            override fun afterTextChanged(s: Editable?) {
                // Not used in this case
            }
        })

        val contentLayout = findViewById<RelativeLayout>(R.id.contentLayout)
        contentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                searchResultsPopup?.dismiss()
                isDropdownDismissed = true
            }
            false
        }

        searchBar.setOnClickListener {
            if (isDropdownDismissed) {
                showSearchResultsDropdown()
                isDropdownDismissed = false
            }
        }

        fetchReminderOption()
    }

    fun updateEventCountSubscript(count: Int) {
        val eventCountTextView = findViewById<TextView>(R.id.eventCount)

        if (count > 0) {
            eventCountTextView.text = count.toString()
            eventCountTextView.visibility = View.VISIBLE
        } else {
            eventCountTextView.visibility = View.INVISIBLE
        }
    }


    private fun fetchReminderOption() {
        val email = fAuth.currentUser?.email.toString()
        if (email.isNotBlank()) {
            val userDocRef = fStore.collection("users").document(email)

            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                val reminderOption = documentSnapshot.getString("reminderOption")

                if (!reminderOption.isNullOrBlank()) {
                    when (reminderOption) {
                        "day" -> {
                            displayEventCount(Calendar.DAY_OF_YEAR)
                        }
                        "month" -> {
                            displayEventCount(Calendar.MONTH)
                        }
                        "year" -> {
                            displayEventCount(Calendar.YEAR)
                        }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to fetch reminder option: ${e.message}")
            }
        }
    }



    private fun displayEventCount(timeFrame: Int) {
        val email = fAuth.currentUser?.email.toString()
        if (email.isNotBlank()) {
            val userDocRef = fStore.collection("users").document(email)

            val currentDate = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                val calendarEvents = documentSnapshot.get("calendarEvents") as? Map<String, List<Map<String, Any>>>

                if (calendarEvents != null) {
                    val eventsInTimeFrame = calendarEvents.filter { (dateKey, events) ->
                        val date = dateFormat.parse(dateKey)
                        val calendar = Calendar.getInstance()
                        calendar.time = date
                        currentDate.get(timeFrame) == calendar.get(timeFrame)
                    }

                    val eventCount = eventsInTimeFrame.size

                    val eventCountTextView = findViewById<TextView>(R.id.eventCount)
                    if (eventCount > 0) {
                        eventCountTextView.text = eventCount.toString()
                        eventCountTextView.visibility = View.VISIBLE
                        eventCountTextView.setOnClickListener {
                            if (eventsInTimeFrame.isNotEmpty()) {
                                val dialog = Dialog(this)
                                dialog.setContentView(R.layout.notification_popup)
                                dialog.setCanceledOnTouchOutside(false)

                                val popupTitle = dialog.findViewById<TextView>(R.id.popupTitle)
                                val popupDateTime = dialog.findViewById<TextView>(R.id.popupDateTime)
                                val popupCloseButton = dialog.findViewById<Button>(R.id.popupCloseButton)

                                popupTitle.setTextColor(Color.BLACK)
                                popupDateTime.setTextColor(Color.BLACK)
                                popupCloseButton.setTextColor(Color.BLACK)

                                val popupContent = StringBuilder()
                                for ((dateKey, events) in eventsInTimeFrame) {
                                    for (eventDetails in events) {
                                        val title = eventDetails["title"]
                                        val dateTime = eventDetails["dateTime"] as Long

                                        val date = dateFormat.parse(dateKey)
                                        val formattedDate = SimpleDateFormat("yy-MM-yyyy", Locale.getDefault()).format(date)
                                        val formattedTime = timeFormat.format(Date(dateTime))

                                        popupContent.append("Event: $title\n")
                                        popupContent.append("Date: $formattedDate\n")
                                        popupContent.append("Time: $formattedTime\n\n")
                                    }
                                }

                                popupTitle.text = "Upcoming Events"
                                popupDateTime.text = popupContent.toString()


                                popupCloseButton.setOnClickListener {
                                    dialog.dismiss()
                                }

                                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                                dialog.show()
                            }
                        }
                    } else {
                        eventCountTextView.visibility = View.GONE
                    }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch event data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun performSymbolSearch(query: String) {
        Log.d("MainActivity", "performSymbolSearch called with query: $query")

        GlobalScope.launch(Dispatchers.IO) {
            val searchResults = searchSymbol(query)

            withContext(Dispatchers.Main) {
                stockSymbolsList.clear()

                if (searchResults != null) {
                    val resultsArray = searchResults.getJSONArray("result")
                    for (i in 0 until resultsArray.length()) {
                        val stockObject = resultsArray.getJSONObject(i)
                        val description = stockObject.getString("description")
                        val displaySymbol = stockObject.getString("displaySymbol")
                        stockSymbolsList.add(StockSymbol(description, displaySymbol))
                    }
                }

                Log.d("MainActivity", "Search results: $stockSymbolsList")

                searchResultsAdapter.notifyDataSetChanged()

                showSearchResultsDropdown()
            }
        }
    }


    private fun showSearchResultsDropdown() {

        searchResultsPopup?.dismiss()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.search_results_dropdown, null)

        searchResultsPopup = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )

        searchResultsPopup?.showAsDropDown(searchBar)

        val dropdownRecyclerView = view.findViewById<RecyclerView>(R.id.dropdownRecyclerView)
        dropdownRecyclerView.layoutManager = LinearLayoutManager(this)
        dropdownRecyclerView.adapter = searchResultsAdapter

        searchResultsAdapter.setOnItemClickListener { stockSymbol ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            Log.d("currentFragment", currentFragment.toString())
            Log.d("newsFragment", NewsFragment.toString())

            if (currentFragment is NewsFragment) {
                currentFragment.fetchNewsData(stockSymbol.displaySymbol)
            } else {
                val intent = Intent(this, DetailsActivity::class.java)
                intent.putExtra("symbol", stockSymbol.displaySymbol)
                startActivity(intent)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.side_news -> openFragment(NewsFragment())
            R.id.side_calendar -> openFragment(CalendarFragment())
            R.id.side_profile -> openFragment(ProfileFragment())
            R.id.side_settings -> openFragment(SettingsFragment())
            R.id.sidebar_logout -> logout()
        }

        selectedSideItemId = item.itemId
        val previousBottomItem = binding.bottomNavigation.menu.findItem(selectedBottomItemId)
        previousBottomItem?.isChecked = false

        binding.drawerLayout.closeDrawer(GravityCompat.START) // sidebar closes automatically
        return true
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)){
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    fun updateSidebar(username: String, imageUrl: String) {
        val navHeader = binding.navigationDrawer.getHeaderView(0)
        val profileImageView = navHeader.findViewById<ImageView>(R.id.profileImageView)
        val profileUsername = navHeader.findViewById<TextView>(R.id.profileUsername)

        profileUsername.text = username

        if (!imageUrl.isNullOrBlank()) {
            Picasso.get().load(imageUrl).into(profileImageView)
        }
    }

    private fun openFragment(fragment: Fragment){
        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment)
        fragmentTransaction.commit()
    }

    private val finhubApiKey = "civ1rihr01qoivqqgj0gciv1rihr01qoivqqgj10"

    private fun searchSymbol(query: String): JSONObject? {
        val baseUrl = "https://finnhub.io/api/v1"
        val searchUrl = "$baseUrl/search?q=$query&token=$finhubApiKey"

        try {
            val url = URL(searchUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }

            reader.close()
            inputStream.close()
            connection.disconnect()

            return JSONObject(response.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}