package dev.rosewind.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

private const val UA = "Rosewind/1.3 (https://github.com/rxvy-dev/rosewind)"
data class Place(val name: String, val sub: String, val lat: Double, val lon: Double, val wiki: String?)

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var me: MyLocationNewOverlay
    private lateinit var labels: TilesOverlay
    private lateinit var scale: ScaleBarOverlay
    private var pin: Marker? = null
    private var layer = 0
    private var appliedFont: String? = null
    private val chipIds = listOf(R.id.chipMap, R.id.chipSat, R.id.chipTopo)

    // Esri tile URLs are z/y/x
    private fun esri(name: String, path: String, ext: String): OnlineTileSourceBase = object : OnlineTileSourceBase(
        name, 0, 19, 256, ext, arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$path/MapServer/tile/"), "© Esri, Maxar"
    ) {
        override fun getTileURLString(i: Long) = "$baseUrl${MapTileIndex.getZoom(i)}/${MapTileIndex.getY(i)}/${MapTileIndex.getX(i)}"
    }
    private val sources = listOf(TileSourceFactory.MAPNIK, esri("EsriSat", "World_Imagery", ".jpg"), TileSourceFactory.OpenTopo)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun night() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(b: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(Style.night(this))
        super.onCreate(b)
        appliedFont = Style.prefs(this).getString("font", "default")
        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osm", 0))
            userAgentValue = packageName
            tileDownloadThreads = 4.toShort(); tileFileSystemThreads = 4.toShort()
            tileDownloadMaxQueueSize = 60.toShort(); tileFileSystemMaxQueueSize = 60.toShort()
            tileFileSystemCacheMaxBytes = 800L * 1024 * 1024; tileFileSystemCacheTrimBytes = 600L * 1024 * 1024
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        val root = findViewById<FrameLayout>(R.id.root)
        val top = findViewById<View>(R.id.top)
        val fab = findViewById<FloatingActionButton>(R.id.locate)
        val q = findViewById<EditText>(R.id.query)
        val clear = findViewById<ImageButton>(R.id.clear)
        WindowCompat.getInsetsController(window, root)?.apply { isAppearanceLightStatusBars = !night(); isAppearanceLightNavigationBars = !night() }

        map = findViewById(R.id.map)
        map.apply {
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 3.0
        }
        val sp = getSharedPreferences("view", 0)
        map.controller.setZoom(sp.getFloat("z", 3f).toDouble())
        map.controller.setCenter(GeoPoint(sp.getFloat("lat", 20f).toDouble(), sp.getFloat("lon", 0f).toDouble()))

        val provider = MapTileProviderBasic(applicationContext)
        provider.setTileSource(esri("EsriLabels", "Reference/World_Boundaries_and_Places", ".png"))
        labels = TilesOverlay(provider, this).apply { loadingBackgroundColor = Color.TRANSPARENT; loadingLineColor = Color.TRANSPARENT }
        me = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        scale = ScaleBarOverlay(map).apply { setAlignBottom(true) }
        map.overlays.addAll(listOf(me, scale))
        chipIds.forEachIndexed { i, id -> findViewById<Chip>(id).setOnClickListener { setLayer(i) } }
        setLayer(sp.getInt("layer", 0))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(dp(12), bars.top + dp(12), dp(12), 0)
            (fab.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(20)
            fab.requestLayout()
            scale.setScaleBarOffset(dp(16), bars.bottom + dp(24))
            ins
        }
        fab.setOnClickListener { locate() }
        findViewById<ImageButton>(R.id.settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        q.setOnEditorActionListener { v, _, _ -> search(v.text.toString()); true }
        q.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })
        clear.setOnClickListener { q.text.clear(); pin?.let { map.overlays.remove(it); pin = null; map.invalidate() } }
    }

    private fun setLayer(i: Int) {
        layer = i
        map.setTileSource(sources[i])
        findViewById<Chip>(chipIds[i]).isChecked = true
        applyPrefs()
    }

    private fun applyPrefs() {
        val s = Style.prefs(this)
        scale.setUnitsOfMeasure(if (s.getInt("units", 0) == 1) ScaleBarOverlay.UnitsOfMeasure.imperial else ScaleBarOverlay.UnitsOfMeasure.metric)
        val want = layer == 1 && s.getBoolean("labels", true)
        val has = map.overlays.contains(labels)
        if (want && !has) map.overlays.add(0, labels) else if (!want && has) map.overlays.remove(labels)
        map.overlayManager.tilesOverlay.setColorFilter(if (night() && layer != 1) TilesOverlay.INVERT_COLORS else null)
        Style.refont(this, findViewById(R.id.root))
        map.invalidate()
    }

    private fun locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1); return
        }
        me.enableMyLocation()
        val go = { p: GeoPoint -> map.controller.setZoom(16.0); map.controller.animateTo(p) }
        val loc = me.myLocation
        if (loc != null) go(loc) else me.runOnFirstFix { runOnUiThread { me.myLocation?.let(go) } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) locate()
    }

    // ---- network ----
    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA); c.connectTimeout = 8000; c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun photos(lat: Double, lon: Double, n: Int): List<String> {
        for (r in listOf(800, 8000)) try {
            val pages = JSONObject(http("https://commons.wikimedia.org/w/api.php?action=query&generator=geosearch&ggscoord=$lat%7C$lon&ggsradius=$r&ggslimit=${n * 2}&ggsnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json"))
                .optJSONObject("query")?.optJSONObject("pages") ?: continue
            val out = pages.keys().asSequence().mapNotNull { k ->
                val p = pages.getJSONObject(k); val t = p.optString("title").lowercase()
                if (t.endsWith(".jpg") || t.endsWith(".jpeg") || t.endsWith(".png"))
                    p.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")?.takeIf { it.isNotEmpty() } else null
            }.take(n).toList()
            if (out.isNotEmpty()) return out
        } catch (e: Exception) {}
        return emptyList()
    }

    private fun wikiInfo(wiki: String): Pair<String, String?>? = try {
        val (lang, title) = wiki.split(":", limit = 2)
        val j = JSONObject(http("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")))
        j.optString("extract") to j.optJSONObject("thumbnail")?.optString("source")
    } catch (e: Exception) { null }

    private fun glide(url: String, iv: ImageView) {
        Glide.with(this).load(GlideUrl(url, LazyHeaders.Builder().addHeader("User-Agent", UA).build())).centerCrop().into(iv)
    }

    // ---- search, results page, place page ----
    private fun search(text: String) {
        if (text.isBlank()) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(map.windowToken, 0)
        thread {
            try {
                val r = JSONArray(http("https://nominatim.openstreetmap.org/search?format=json&limit=8&extratags=1&q=" + URLEncoder.encode(text, "UTF-8")))
                val list = (0 until r.length()).map { i ->
                    val o = r.getJSONObject(i); val parts = o.getString("display_name").split(", ", limit = 2)
                    Place(parts[0], parts.getOrElse(1) { "" }, o.getDouble("lat"), o.getDouble("lon"),
                        o.optJSONObject("extratags")?.optString("wikipedia")?.takeIf { it.isNotEmpty() })
                }
                runOnUiThread { if (list.isEmpty()) toast("No results") else showResults(list, text) }
            } catch (e: Exception) { runOnUiThread { toast("Search failed") } }
        }
    }

    private fun showResults(list: List<Place>, query: String) {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_results, null)
        v.findViewById<TextView>(R.id.title).text = "Results for “$query”"
        val box = v.findViewById<LinearLayout>(R.id.list)
        list.forEach { p ->
            val row = layoutInflater.inflate(R.layout.item_place, box, false)
            row.findViewById<TextView>(R.id.name).text = p.name
            row.findViewById<TextView>(R.id.sub).text = p.sub
            val iv = row.findViewById<ImageView>(R.id.thumb)
            thread { photos(p.lat, p.lon, 1).firstOrNull()?.let { u -> runOnUiThread { if (!isDestroyed) glide(u, iv) } } }
            row.setOnClickListener { d.dismiss(); drop(p); details(p) }
            box.addView(row)
        }
        Style.refont(this, v); d.setContentView(v); d.behavior.state = BottomSheetBehavior.STATE_EXPANDED; d.show()
    }

    private fun drop(p: Place) {
        pin?.let { map.overlays.remove(it) }
        val m = Marker(map).apply {
            position = GeoPoint(p.lat, p.lon); icon = getDrawable(R.drawable.ic_pin); title = p.name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ -> details(p); true }
        }
        pin = m; map.overlays.add(m)
        map.controller.setZoom(15.0); map.controller.animateTo(m.position); map.invalidate()
    }

    private fun details(p: Place) {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_detail, null)
        v.findViewById<TextView>(R.id.name).text = p.name
        v.findViewById<TextView>(R.id.sub).text = p.sub
        val desc = v.findViewById<TextView>(R.id.desc)
        val row = v.findViewById<LinearLayout>(R.id.photoRow)
        v.findViewById<View>(R.id.directions).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(p.name)})"))) }
            catch (e: Exception) { toast("No maps app found") }
        }
        v.findViewById<View>(R.id.share).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${p.name}\nhttps://www.openstreetmap.org/?mlat=${p.lat}&mlon=${p.lon}#map=17/${p.lat}/${p.lon}"), null))
        }
        thread {
            val w = p.wiki?.let { wikiInfo(it) }
            val urls = (listOfNotNull(w?.second?.takeIf { it.isNotEmpty() }) + photos(p.lat, p.lon, 10)).distinct()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val text = w?.first.orEmpty()
                desc.text = text; desc.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                if (urls.isEmpty()) { v.findViewById<View>(R.id.photos).visibility = View.GONE; v.findViewById<View>(R.id.noPhotos).visibility = View.VISIBLE }
                urls.forEach { u ->
                    val iv = ShapeableImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(dp(16).toFloat()).build()
                    }
                    row.addView(iv, LinearLayout.LayoutParams(dp(240), dp(180)).apply { marginEnd = dp(8) })
                    glide(u, iv)
                }
                Style.refont(this, v)
            }
        }
        Style.refont(this, v); d.setContentView(v); d.behavior.state = BottomSheetBehavior.STATE_EXPANDED; d.show()
    }

    override fun onResume() {
        super.onResume(); map.onResume()
        if (Style.prefs(this).getString("font", "default") != appliedFont) { recreate(); return }
        applyPrefs()
    }
    override fun onPause() {
        super.onPause(); map.onPause()
        getSharedPreferences("view", 0).edit().putFloat("lat", map.mapCenter.latitude.toFloat())
            .putFloat("lon", map.mapCenter.longitude.toFloat()).putFloat("z", map.zoomLevelDouble.toFloat()).putInt("layer", layer).apply()
    }
}
