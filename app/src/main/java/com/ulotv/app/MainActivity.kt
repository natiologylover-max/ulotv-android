package com.ulotv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private const val TMDB_KEY = "4b98c9ea0e913ff238b0aa9a18e7e11f"
private const val TMDB_IMAGE = "https://image.tmdb.org/t/p/w500/"
private const val EMBEDMASTER_ID = "v2w3r1tv0k9bhqzdza"

data class TitleItem(
    val id: Int,
    val name: String,
    val overview: String,
    val poster: String,
    val backdrop: String,
    val rating: String,
    val year: String,
    val type: String
)

class MainActivity : Activity() {
    private val executor = Executors.newFixedThreadPool(4)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var searchBox: EditText
    private var currentTab = "Home"
    private var selected: TitleItem? = null
    private val saved = linkedMapOf<Int, TitleItem>()
    private val ink = Color.rgb(8, 10, 17)
    private val panel = Color.rgb(22, 25, 37)
    private val muted = Color.rgb(166, 174, 192)
    private val accent = Color.rgb(116, 91, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        showHome()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun button(text: String, action: () -> Unit, primary: Boolean = false): TextView =
        label(text, 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = bg(if (primary) accent else panel, 12)
            setOnClickListener { action() }
        }
    private fun setup(title: String, showSearch: Boolean = true) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
            setBackgroundColor(ink)
        }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = label("ULO", 25f, Color.WHITE, true)
        val tv = label(" TV", 25f, accent, true)
        brandRow.addView(brand); brandRow.addView(tv)
        brandRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        brandRow.addView(label("●", 10f, Color.rgb(70, 220, 150), true))
        header.addView(brandRow)
        header.addView(label(title, 23f, Color.WHITE, true).apply {
            setPadding(0, dp(13), 0, dp(8))
        })
        if (showSearch) {
            searchBox = EditText(this).apply {
                hint = "Search movies and TV shows"
                textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(muted)
                setSingleLine(true)
                setPadding(dp(14), 0, dp(14), 0)
                background = bg(panel, 12)
            }
            header.addView(searchBox, LinearLayout.LayoutParams(-1, dp(46)))
            searchBox.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrBlank()) searchTitles(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(panel)
            gravity = Gravity.CENTER
        }
        listOf("Home", "Movies", "TV Shows", "My List").forEach { item ->
            val v = label(if (item == currentTab) "●  $item" else item, 12f,
                if (item == currentTab) Color.WHITE else muted, item == currentTab)
            v.gravity = Gravity.CENTER
            v.setPadding(dp(8), dp(12), dp(8), dp(12))
            nav.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
            v.setOnClickListener {
                currentTab = item
                when (item) {
                    "Home" -> showHome()
                    "Movies" -> showCategory("movie", "Popular Movies")
                    "TV Shows" -> showCategory("tv", "Popular TV Shows")
                    else -> showWatchlist()
                }
            }
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun request(path: String, callback: (JSONObject?) -> Unit) {
        executor.execute {
            var result: JSONObject? = null
            try {
                val conn = URL("https://api.themoviedb.org/3$path" +
                    (if (path.contains("?")) "&" else "?") + "api_key=$TMDB_KEY")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 12000; conn.readTimeout = 12000
                result = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
            } catch (_: Exception) {}
            runOnUiThread { callback(result) }
        }
    }

    private fun parse(item: JSONObject, type: String): TitleItem {
        val name = item.optString(if (type == "movie") "title" else "name", "Untitled")
        val date = item.optString(if (type == "movie") "release_date" else "first_air_date", "")
        return TitleItem(item.optInt("id"), name, item.optString("overview"),
            item.optString("poster_path"), item.optString("backdrop_path"),
            String.format("%.1f", item.optDouble("vote_average")), date.take(4), type)
    }

    private fun showHome() {
        currentTab = "Home"; setup("Discover")
        content.addView(label("Your next favorite starts here", 16f, muted))
        content.addView(label("TRENDING THIS WEEK", 12f, accent, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        })
        loadRow("/trending/all/week", "all", true)
        content.addView(label("POPULAR MOVIES", 12f, accent, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        })
        loadRow("/movie/popular", "movie", false)
        content.addView(label("POPULAR SERIES", 12f, accent, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        })
        loadRow("/tv/popular", "tv", false)
    }

    private fun showCategory(type: String, title: String) {
        currentTab = if (type == "movie") "Movies" else "TV Shows"
        setup(title)
        val progress = label("Loading titles…", 14f, muted)
        content.addView(progress)
        request("/${type}/popular?language=en-US&page=1") { json ->
            content.removeView(progress)
            val results = json?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) addCard(parse(results.getJSONObject(i), type))
            if (results.length() == 0) content.addView(label("Couldn't load titles. Check your internet connection.", 14f, muted))
        }
    }

    private fun loadRow(path: String, fallbackType: String, trending: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(ScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LinearLayout.LayoutParams(-1, dp(220)))
        request("$path?language=en-US&page=1") { json ->
            val results = json?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until minOf(results.length(), 12)) {
                val obj = results.getJSONObject(i)
                val type = if (trending) obj.optString("media_type", fallbackType).let {
                    if (it == "tv" || it == "movie") it else fallbackType
                } else fallbackType
                val item = parse(obj, type)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(4), dp(2), dp(8), dp(4))
                }
                val poster = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = bg(panel, 10)
                }
                card.addView(poster, LinearLayout.LayoutParams(dp(122), dp(164)))
                card.addView(label(item.name, 12f, Color.WHITE, true).apply {
                    maxLines = 2; setPadding(0, dp(6), 0, 0)
                }, LinearLayout.LayoutParams(dp(122), -2))
                card.setOnClickListener { showDetails(item) }
                row.addView(card)
                if (item.poster.isNotBlank()) loadPoster(item.poster) { bitmap -> poster.setImageBitmap(bitmap) }
            }
        }
    }

    private fun addCard(item: TitleItem) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(panel, 8) }
        row.addView(poster, LinearLayout.LayoutParams(dp(88), dp(126)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
        }
        info.addView(label(item.name, 15f, Color.WHITE, true))
        info.addView(label("★ ${item.rating}  •  ${item.year}  •  ${item.type.uppercase()}", 12f, muted).apply {
            setPadding(0, dp(5), 0, dp(8))
        })
        info.addView(label(item.overview.ifBlank { "Open to see the full synopsis." }, 12f, muted).apply { maxLines = 3 })
        info.addView(button("View details", { showDetails(item) }, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
        content.addView(View(this).apply { setBackgroundColor(panel) }, LinearLayout.LayoutParams(-1, dp(1)))
        if (item.poster.isNotBlank()) loadPoster(item.poster) { poster.setImageBitmap(it) }
    }

    private fun loadPoster(path: String, callback: (android.graphics.Bitmap?) -> Unit) {
        executor.execute {
            val bitmap = try {
                val c = URL(TMDB_IMAGE + path.trimStart('/')).openConnection()
                c.connectTimeout = 8000; c.readTimeout = 8000
                BitmapFactory.decodeStream(c.getInputStream())
            } catch (_: Exception) { null }
            runOnUiThread { callback(bitmap) }
        }
    }

    private fun showDetails(item: TitleItem) {
        selected = item
        setup(item.name, false)
        content.addView(label("★ ${item.rating}   •   ${item.year}   •   ${item.type.uppercase()}", 13f, muted))
        if (item.backdrop.isNotBlank()) {
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(panel, 14) }
            content.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
            loadPoster(item.backdrop) { image.setImageBitmap(it) }
        } else if (item.poster.isNotBlank()) {
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(panel, 14) }
            content.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
            loadPoster(item.poster) { image.setImageBitmap(it) }
        }
        content.addView(label(item.overview.ifBlank { "No synopsis available." }, 14f, muted).apply {
            setPadding(0, dp(16), 0, dp(18))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button("▶  Watch now", { showPlayer(item) }, true), LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(button(if (saved.containsKey(item.id)) "✓  Added" else "＋  My List", {
            if (saved.containsKey(item.id)) saved.remove(item.id) else saved[item.id] = item
            showDetails(item)
        }), LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(actions)
        content.addView(label("MORE LIKE THIS", 12f, accent, true).apply { setPadding(0, dp(24), 0, dp(8)) })
        val similarType = item.type
        request("/${similarType}/${item.id}/similar?language=en-US&page=1") { json ->
            val results = json?.optJSONArray("results") ?: JSONArray()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            content.addView(ScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            for (i in 0 until minOf(results.length(), 10)) {
                val similar = parse(results.getJSONObject(i), similarType)
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(2), dp(8), dp(4)) }
                val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(panel, 10) }
                card.addView(poster, LinearLayout.LayoutParams(dp(122), dp(164)))
                card.addView(label(similar.name, 12f, Color.WHITE, true).apply { maxLines = 2 }, LinearLayout.LayoutParams(dp(122), -2))
                card.setOnClickListener { showDetails(similar) }
                row.addView(card)
                if (similar.poster.isNotBlank()) loadPoster(similar.poster) { poster.setImageBitmap(it) }
            }
        }
    }

    private fun showPlayer(item: TitleItem) {
        setup("Now playing", false)
        content.addView(label(item.name, 20f, Color.WHITE, true))
        content.addView(label("Player opens when you press Play. Rotate your phone for landscape.", 13f, muted).apply {
            setPadding(0, dp(8), 0, dp(12))
        })
        if (item.type == "movie") {
            content.addView(label("Preparing movie player…", 14f, muted))
            request("/movie/${item.id}/external_ids") { ids ->
                val imdb = ids?.optString("imdb_id", "").orEmpty()
                if (imdb.startsWith("tt")) addPlayerView(item, "https://embedmaster.link/$EMBEDMASTER_ID/movie/$imdb")
                else content.addView(label("An IMDb ID wasn't found for this movie, so the player link can't be created.", 13f, muted))
            }
        } else {
            addPlayerView(item, "https://embedmaster.link/$EMBEDMASTER_ID/tv/${item.id}/1/1")
        }
    }

    private fun addPlayerView(item: TitleItem, url: String) {
        val web = android.webkit.WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = android.webkit.WebChromeClient()
            webViewClient = android.webkit.WebViewClient()
            loadUrl(url)
        }
        content.addView(web, LinearLayout.LayoutParams(-1, dp(240)))
        content.addView(label("If playback doesn't load, check your connection and try again.", 12f, muted).apply {
            setPadding(0, dp(10), 0, dp(10))
        })
        content.addView(button("← Back to details", { showDetails(item) }))
    }

    private fun searchTitles(query: String) {
        if (!::content.isInitialized) return
        val encoded = URLEncoder.encode(query, "UTF-8")
        request("/search/multi?query=$encoded&include_adult=false&language=en-US&page=1") { json ->
            if (currentTab == "Home" && query.isNotBlank()) {
                content.removeAllViews()
                content.addView(label("SEARCH RESULTS", 12f, accent, true))
                val results = json?.optJSONArray("results") ?: JSONArray()
                for (i in 0 until minOf(results.length(), 20)) {
                    val obj = results.getJSONObject(i)
                    val type = obj.optString("media_type")
                    if (type == "movie" || type == "tv") addCard(parse(obj, type))
                }
                if (results.length() == 0) content.addView(label("No results found.", 14f, muted))
            }
        }
    }

    private fun showWatchlist() {
        currentTab = "My List"; setup("My List", false)
        if (saved.isEmpty()) {
            content.addView(label("Your list is empty", 20f, Color.WHITE, true))
            content.addView(label("Save movies and series from their detail pages to find them here.", 14f, muted))
        } else saved.values.forEach { addCard(it) }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
