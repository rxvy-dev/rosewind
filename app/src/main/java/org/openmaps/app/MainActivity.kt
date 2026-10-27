package org.openmaps.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var me: MyLocationNewOverlay
    private var pin: Marker? = null
    private var li = 0

    // Esri World Imagery (OSM has no satellite imagery). Esri URL order is z/y/x.
    private val sat = object : OnlineTileSourceBase(
        "EsriSat", 0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Imagery © Esri, Maxar, Earthstar Geographics"
    ) {
        override fun getTileURLString(i: Long) =
            "$baseUrl${MapTileIndex.getZoom(i)}/${MapTileIndex.getY(i)}/${MapTileIndex.getX(i)}"
    }
    private val layers = listOf("Street" to TileSourceFactory.MAPNIK, "Satellite" to sat, "Topo" to TileSourceFactory.OpenTopo)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Configuration.getInstance().apply { load(this@MainActivity, getSharedPreferences("osm", 0)); userAgentValue = packageName }
        map = MapView(this).apply {
            setMultiTouchControls(true)
            controller.setZoom(3.0); controller.setCenter(GeoPoint(20.0, 0.0))
        }
        me = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(me)

        val q = EditText(this).apply {
            hint = "Search places"; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setBackgroundColor(Color.WHITE); setTextColor(Color.BLACK); setHintTextColor(Color.GRAY); setPadding(32, 32, 32, 32)
            setOnEditorActionListener { v, _, _ -> search(v.text.toString()); true }
        }
        val layerBtn = Button(this).apply {
            text = layers[0].first
            setOnClickListener { li = (li + 1) % layers.size; map.setTileSource(layers[li].second); text = layers[li].first }
        }
        val locBtn = Button(this).apply { text = "Locate"; setOnClickListener { locate() } }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(layerBtn); addView(locBtn) }

        setContentView(FrameLayout(this).apply {
            addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(q, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, 24, 96) })
        })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

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
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) locate()
    }

    private fun search(text: String) = thread {
        try {
            val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(text, "UTF-8")
            val c = URL(url).openConnection().apply { setRequestProperty("User-Agent", packageName) }
            val r = JSONArray(c.getInputStream().bufferedReader().readText())
            runOnUiThread {
                if (r.length() == 0) { toast("Not found"); return@runOnUiThread }
                val o = r.getJSONObject(0)
                val p = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                pin?.let { map.overlays.remove(it) }
                pin = Marker(map).apply { position = p; title = o.getString("display_name") }
                map.overlays.add(pin); map.controller.setZoom(15.0); map.controller.animateTo(p); map.invalidate()
            }
        } catch (e: Exception) { runOnUiThread { toast("Search failed") } }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}
