package dev.wayglass.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
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
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var me: MyLocationNewOverlay
    private lateinit var labels: TilesOverlay
    private var pin: Marker? = null
    private var layer = 0
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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
        WindowCompat.getInsetsController(window, root)?.apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true }

        map = findViewById(R.id.map)
        map.apply {
            isTilesScaledToDpi = true   // far fewer, sharper tiles = much smoother
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
        val scale = ScaleBarOverlay(map).apply { setAlignBottom(true) }
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
        if (i == 1) { if (!map.overlays.contains(labels)) map.overlays.add(0, labels) } else map.overlays.remove(labels)
        findViewById<Chip>(chipIds[i]).isChecked = true
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

    private fun search(text: String) {
        if (text.isBlank()) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(map.windowToken, 0)
        thread {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(text, "UTF-8")
                val c = URL(url).openConnection().apply { setRequestProperty("User-Agent", packageName) }
                val r = JSONArray(c.getInputStream().bufferedReader().readText())
                runOnUiThread {
                    if (r.length() == 0) { toast("No results"); return@runOnUiThread }
                    val o = r.getJSONObject(0)
                    val p = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                    val parts = o.getString("display_name").split(", ", limit = 2)
                    pin?.let { map.overlays.remove(it) }
                    val m = Marker(map).apply {
                        position = p; icon = getDrawable(R.drawable.ic_pin); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = parts[0]; snippet = parts.getOrElse(1) { "" }
                    }
                    pin = m; map.overlays.add(m); map.controller.setZoom(15.0); map.controller.animateTo(p); m.showInfoWindow(); map.invalidate()
                }
            } catch (e: Exception) { runOnUiThread { toast("Search failed") } }
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() {
        super.onPause(); map.onPause()
        getSharedPreferences("view", 0).edit().putFloat("lat", map.mapCenter.latitude.toFloat())
            .putFloat("lon", map.mapCenter.longitude.toFloat()).putFloat("z", map.zoomLevelDouble.toFloat()).putInt("layer", layer).apply()
    }
}
