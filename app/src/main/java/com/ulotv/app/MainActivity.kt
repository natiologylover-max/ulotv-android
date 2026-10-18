package com.ulotv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private const val TMDB_KEY = "4b98c9ea0e913ff238b0aa9a18e7e11f"
private const val IMG = "https://image.tmdb.org/t/p/"
private const val EMBEDMASTER_ID = "v2w3r1tv0k9bhqzdza"

data class TitleItem(
    val id: Int, val name: String, val overview: String, val poster: String,
    val backdrop: String, val rating: String, val year: String, val type: String
)

class MainActivity : Activity() {
    private val io = Executors.newFixedThreadPool(5)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private var tab = "Home"
    private var searchMode = false
    private val saved = linkedMapOf<Int, TitleItem>()
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(32 * 1024 * 1024) {}
    private val bg = Color.rgb(8, 10, 16)
    private val surface = Color.rgb(19, 23, 33)
    private val surface2 = Color.rgb(29, 34, 46)
    private val textMuted = Color.rgb(151, 161, 181)
    private val purple = Color.rgb(151, 103, 255)
    private val lime = Color.rgb(204, 255, 92)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        showHome()
    }

    private fun d(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 16): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = d(radius).toFloat() }
    private fun gradient(top: Int, bottom: Int, radius: Int = 24): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply { cornerRadius = d(radius).toFloat() }
    private fun txt(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        }
    private fun pill(s: String, action: () -> Unit, primary: Boolean = false): TextView =
        txt(s, 13f, if (primary) bg else Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(d(17), d(12), d(17), d(12))
            background = shape(if (primary) lime else surface2, 30); setOnClickListener { action() }
        }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, d(h)) }

    private fun shell(title: String, subtitle: String = "", searchBar: Boolean = true) {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(12), d(20), d(12)); setBackgroundColor(bg)
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "U"; textSize = 20f; typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(bg); background = shape(lime, 13)
        }
        bar.addView(logo, LinearLayout.LayoutParams(d(42), d(42)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(10), 0, 0, 0) }
        brand.addView(txt("ULO TV", 19f, Color.WHITE, true))
        brand.addView(txt("YOUR NEXT OBSESSION", 8f, textMuted, true).apply { letterSpacing = .12f; setPadding(0, d(3), 0, 0) })
        bar.addView(brand)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val searchButton = TextView(this).apply {
            text = "⌕"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = shape(surface, 14)
            setOnClickListener { toggleSearch() }
        }
        bar.addView(searchButton, LinearLayout.LayoutParams(d(44), d(44)))
        top.addView(bar)
        if (title.isNotBlank()) {
            top.addView(txt(title, 27f, Color.WHITE, true).apply { setPadding(0, d(18), 0, d(4)) })
            if (subtitle.isNotBlank()) top.addView(txt(subtitle, 12f, textMuted).apply { setPadding(0, 0, 0, d(8)) })
        }
        if (searchBar && searchMode) {
            search = EditText(this).apply {
                hint = "Titles, genres, people"; textSize = 14f; setTextColor(Color.WHITE)
                setHintTextColor(textMuted); singleLine = true; setPadding(d(15), 0, d(15), 0)
                background = shape(surface, 14)
            }
            top.addView(search, LinearLayout.LayoutParams(-1, d(48)))
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString().orEmpty()
                    if (q.length >= 2) searchTitles(q)
                    if (q.isEmpty() && tab == "Home") showHome()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(top)
        val scroll = ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, d(4), 0, d(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(d(10), d(8), d(10), d(8)); background = shape(Color.rgb(15, 18, 27), 22)
        }
        val navWrap = FrameLayout(this).apply {
            setPadding(d(12), d(6), d(12), d(8)); setBackgroundColor(bg)
        }
        listOf(Triple("Home", "⌂", "Home"), Triple("Movies", "▣", "Movies"),
            Triple("TV Shows", "▤", "Series"), Triple("My List", "♡", "My List")).forEach { (key, glyph, label) ->
            val active = tab == key
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = if (active) shape(Color.rgb(43, 49, 35), 16) else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                setPadding(d(4), d(7), d(4), d(7))
            }
            item.addView(txt(glyph, 21f, if (active) lime else textMuted, active).apply { gravity = Gravity.CENTER })
            item.addView(txt(label, 10f, if (active) Color.WHITE else textMuted, active).apply {
                gravity = Gravity.CENTER; setPadding(0, d(3), 0, 0)
            })
            nav.addView(item, LinearLayout.LayoutParams(0, d(53), 1f))
            item.setOnClickListener {
                searchMode = false
                when (key) {
                    "Home" -> showHome()
                    "Movies" -> showCategory("movie")
                    "TV Shows" -> showCategory("tv")
                    else -> showSaved()
                }
            }
        }
        navWrap.addView(nav, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(navWrap)
        setContentView(root)
    }

    private fun toggleSearch() {
        searchMode = !searchMode
        if (tab == "Home") showHome() else shell(if (tab == "Movies") "Movies" else "Series", "Find your next favourite", true)
    }

    private fun api(path: String, done: (JSONObject?) -> Unit) {
        io.execute {
            var out: JSONObject? = null
            try {
                val sep = if (path.contains("?")) "&" else "?"
                val conn = URL("https://api.themoviedb.org/3$path${sep}api_key=$TMDB_KEY").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                out = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }); conn.disconnect()
            } catch (_: Exception) {}
            runOnUiThread { if (!isFinishing) done(out) }
        }
    }
    private fun parse(o: JSONObject, type: String): TitleItem {
        val name = o.optString(if (type == "movie") "title" else "name", "Untitled")
        val date = o.optString(if (type == "movie") "release_date" else "first_air_date", "")
        return TitleItem(o.optInt("id"), name, o.optString("overview"), o.optString("poster_path"),
            o.optString("backdrop_path"), String.format("%.1f", o.optDouble("vote_average")), date.take(4), type)
    }

    private fun showHome() {
        tab = "Home"; shell("", "", true)
        val hello = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), d(4))
        }
        hello.addView(txt("MONDAY? MOVIE NIGHT.", 10f, lime, true).apply { letterSpacing = .16f })
        hello.addView(txt("Find your\nnext favourite.", 31f, Color.WHITE, true).apply {
            setPadding(0, d(7), 0, d(10)); setLineSpacing(d(1).toFloat(), 1f)
        })
        content.addView(hello)
        val heroFrame = FrameLayout(this).apply {
            background = gradient(Color.rgb(46, 41, 68), Color.rgb(20, 22, 33), 24)
        }
        val heroText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM
            setPadding(d(18), d(20), d(18), d(18))
        }
        heroText.addView(txt("THE SPOTLIGHT", 9f, lime, true).apply { letterSpacing = .18f })
        heroText.addView(txt("Stories worth\nstaying in for.", 25f, Color.WHITE, true).apply { setPadding(0, d(7), 0, d(12)) })
        heroText.addView(pill("Explore trending  ↗", { loadTrending() }, true).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        })
        heroFrame.addView(heroText, FrameLayout.LayoutParams(-1, -1))
        val heroLp = LinearLayout.LayoutParams(-1, d(230)).apply { setMargins(d(16), d(8), d(16), d(8)) }
        content.addView(heroFrame, heroLp)
        sectionTitle("Trending now", "THIS WEEK")
        loadRow("/trending/all/week", "all", true)
        sectionTitle("Movies people love", "MOVIES")
        loadRow("/movie/popular", "movie", false)
        sectionTitle("Binge-worthy series", "SERIES")
        loadRow("/tv/popular", "tv", false)
    }

    private fun sectionTitle(title: String, tag: String) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(d(20), d(19), d(20), d(10))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(txt(tag, 8f, lime, true).apply { letterSpacing = .16f })
        texts.addView(txt(title, 19f, Color.WHITE, true).apply { setPadding(0, d(4), 0, 0) })
        row.addView(texts)
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(txt("VIEW ALL  ›", 9f, textMuted, true))
        content.addView(row)
    }

    private fun loadRow(path: String, fallback: String, trending: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(16), 0, d(8), 0) }
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
        content.addView(horizontal, LinearLayout.LayoutParams(-1, d(250)))
        api("$path?language=en-US&page=1") { json ->
            val results = json?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until minOf(results.length(), 14)) {
                val o = results.getJSONObject(i)
                val type = if (trending) o.optString("media_type", fallback).let { if (it == "tv" || it == "movie") it else "movie" } else fallback
                val item = parse(o, type)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(d(4), d(4), d(7), d(4))
                }
                val posterWrap = FrameLayout(this).apply { background = shape(surface, 14); clipToOutline = true }
                val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                posterWrap.addView(poster, FrameLayout.LayoutParams(-1, -1))
                val rating = TextView(this).apply {
                    text = "★ ${item.rating}"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(lime); setPadding(d(7), d(5), d(7), d(5)); background = shape(Color.rgb(13, 16, 21), 9)
                }
                posterWrap.addView(rating, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                    setMargins(0, d(7), d(7), 0)
                })
                card.addView(posterWrap, LinearLayout.LayoutParams(d(139), d(186)))
                card.addView(txt(item.name, 12f, Color.WHITE, true).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(d(2), d(8), 0, d(3))
                }, LinearLayout.LayoutParams(d(139), -2))
                card.addView(txt("${item.year}  ·  ${if (type == "tv") "SERIES" else "FILM"}", 9f, textMuted, true))
                card.setOnClickListener { details(item) }
                row.addView(card)
                if (item.poster.isNotBlank()) image(item.poster, "w342") { bitmap -> if (bitmap != null) poster.setImageBitmap(bitmap) }
            }
        }
    }

    private fun categoryCard(item: TitleItem) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(d(16), d(8), d(16), d(12))
        }
        val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(surface, 12) }
        row.addView(poster, LinearLayout.LayoutParams(d(104), d(148)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), 0, 0, 0) }
        info.addView(txt(item.name, 16f, Color.WHITE, true))
        info.addView(txt("★ ${item.rating}   ·   ${item.year}", 11f, lime, true).apply { setPadding(0, d(7), 0, d(8)) })
        info.addView(txt(item.overview.ifBlank { "Discover the story." }, 11f, textMuted).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(pill("View title  →", { details(item) }, false).apply {
            setPadding(d(12), d(9), d(12), d(9)); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(10) }
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row)
        if (item.poster.isNotBlank()) image(item.poster, "w342") { if (it != null) poster.setImageBitmap(it) }
    }

    private fun showCategory(type: String) {
        tab = if (type == "movie") "Movies" else "TV Shows"
        shell(if (type == "movie") "All movies" else "Series to binge", "Handpicked for your next watch")
        val progress = txt("Finding something good…", 13f, textMuted)
        progress.setPadding(d(20), d(12), d(20), d(12)); content.addView(progress)
        api("/$type/popular?language=en-US&page=1") { json ->
            content.removeView(progress)
            val arr = json?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) categoryCard(parse(arr.getJSONObject(i), type))
            if (arr.length() == 0) content.addView(txt("Could not load titles. Check your connection.", 13f, textMuted))
        }
    }

    private fun image(path: String, size: String, done: (android.graphics.Bitmap?) -> Unit) {
        val key = "$size/$path"
        val hit = cache.get(key)
        if (hit != null) { done(hit); return }
        io.execute {
            val bitmap = try {
                val conn = URL(IMG + key.trimStart('/')).openConnection()
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                BitmapFactory.decodeStream(conn.getInputStream())
            } catch (_: Exception) { null }
            if (bitmap != null) cache.put(key, bitmap)
            runOnUiThread { if (!isFinishing) done(bitmap) }
        }
    }

    private fun details(item: TitleItem) {
        tab = tab
        shell("Title details", "A closer look at your next watch", false)
        val backdrop = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(surface, 20) }
        content.addView(backdrop, LinearLayout.LayoutParams(-1, d(210)).apply { setMargins(d(16), d(8), d(16), d(14)) })
        val heroPath = item.backdrop.ifBlank { item.poster }
        if (heroPath.isNotBlank()) image(heroPath, "w780") { if (it != null) backdrop.setImageBitmap(it) }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), 0, d(20), d(20)) }
        wrap.addView(txt(item.name, 28f, Color.WHITE, true))
        wrap.addView(txt("★ ${item.rating}    ·    ${item.year}    ·    ${if (item.type == "tv") "TV SERIES" else "MOVIE"}", 11f, lime, true).apply { setPadding(0, d(9), 0, d(13)) })
        wrap.addView(txt(item.overview.ifBlank { "Synopsis unavailable." }, 13f, textMuted).apply { setLineSpacing(d(4).toFloat(), 1f) })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(18), 0, d(4)) }
        actions.addView(pill("▶   Play now", { player(item) }, true), LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(d(10), 1))
        actions.addView(pill(if (saved.containsKey(item.id)) "✓ Saved" else "＋ My List", {
            if (saved.containsKey(item.id)) saved.remove(item.id) else saved[item.id] = item
            details(item)
        }), LinearLayout.LayoutParams(0, -2, 1f))
        wrap.addView(actions)
        content.addView(wrap)
        sectionTitle("You might also like", "MORE TO EXPLORE")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(16), 0, d(8), 0) }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-1, d(245)))
        api("/${item.type}/${item.id}/similar?language=en-US&page=1") { json ->
            val arr = json?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until minOf(arr.length(), 10)) {
                val o = arr.getJSONObject(i); val similar = parse(o, item.type)
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(4), d(4), d(7), d(4)) }
                val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(surface, 13) }
                card.addView(poster, LinearLayout.LayoutParams(d(126), d(168)))
                card.addView(txt(similar.name, 11f, Color.WHITE, true).apply { maxLines = 1; setPadding(0, d(6), 0, 0) })
                card.setOnClickListener { details(similar) }; row.addView(card)
                if (similar.poster.isNotBlank()) image(similar.poster, "w342") { if (it != null) poster.setImageBitmap(it) }
            }
        }
    }

    private fun player(item: TitleItem) {
        shell("Now playing", item.name, false)
        val frame = FrameLayout(this).apply { background = ColorDrawable(Color.BLACK) }
        content.addView(frame, LinearLayout.LayoutParams(-1, d(245)).apply { setMargins(0, d(10), 0, d(14)) })
        content.addView(txt("If the player does not start, check your connection.", 12f, textMuted).apply {
            setPadding(d(18), d(8), d(18), d(12))
        })
        content.addView(pill("← Back to title", { details(item) }).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(d(18), d(4), 0, d(20)) }
        })
        fun mount(url: String) {
            val web = WebView(this).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadUrl(url)
            }
            frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        }
        if (item.type == "movie") {
            api("/movie/${item.id}/external_ids") { ids ->
                val imdb = ids?.optString("imdb_id", "").orEmpty()
                if (imdb.startsWith("tt")) mount("https://embedmaster.link/$EMBEDMASTER_ID/movie/$imdb")
                else frame.addView(txt("Player source unavailable for this title.", 13f, Color.WHITE).apply { gravity = Gravity.CENTER })
            }
        } else mount("https://embedmaster.link/$EMBEDMASTER_ID/tv/${item.id}/1/1")
    }

    private fun searchTitles(q: String) {
        val encoded = URLEncoder.encode(q, "UTF-8")
        api("/search/multi?query=$encoded&include_adult=false&language=en-US&page=1") { json ->
            if (tab != "Home" && !searchMode) return@api
            content.removeAllViews()
            val arr = json?.optJSONArray("results") ?: JSONArray()
            content.addView(txt("SEARCH RESULTS", 10f, lime, true).apply { setPadding(d(20), d(12), d(20), d(10)) })
            for (i in 0 until minOf(arr.length(), 25)) {
                val o = arr.getJSONObject(i); val type = o.optString("media_type")
                if (type == "movie" || type == "tv") categoryCard(parse(o, type))
            }
            if (arr.length() == 0) content.addView(txt("No titles found yet.", 13f, textMuted).apply { setPadding(d(20), d(12), d(20), d(12)) })
        }
    }

    private fun showSaved() {
        tab = "My List"; shell("Your watchlist", "Your personal movie shelf", false)
        if (saved.isEmpty()) {
            content.addView(txt("Nothing saved. Yet.", 23f, Color.WHITE, true).apply { setPadding(d(20), d(22), d(20), d(6)) })
            content.addView(txt("Tap + My List on any title to keep it here.", 13f, textMuted).apply { setPadding(d(20), 0, d(20), d(20)) })
        } else saved.values.toList().forEach { categoryCard(it) }
    }

    override fun onBackPressed() {
        when {
            tab != "Home" -> showHome()
            else -> super.onBackPressed()
        }
    }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }
}
