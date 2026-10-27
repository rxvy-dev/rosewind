package dev.rosewind.app

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

object Style {
    val fontNames = listOf("System default", "Serif", "Monospace", "Condensed", "Casual", "Cursive", "Light", "Medium", "Black")
    val fontKeys = listOf("default", "serif", "monospace", "sans-serif-condensed", "casual", "cursive", "sans-serif-light", "sans-serif-medium", "sans-serif-black")
    fun prefs(c: Context) = c.getSharedPreferences("settings", 0)
    fun night(c: Context) = when (prefs(c).getInt("theme", 0)) {
        1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    fun typeface(c: Context): Typeface? {
        val f = prefs(c).getString("font", "default") ?: "default"
        if (f == "default") return null
        return if (f.startsWith("/")) try { Typeface.createFromFile(f) } catch (e: Exception) { null } else Typeface.create(f, Typeface.NORMAL)
    }
    private fun font(v: View, tf: Typeface) {
        if (v is TextView) v.typeface = tf
        if (v is ViewGroup) for (i in 0 until v.childCount) font(v.getChildAt(i), tf)
    }
    fun refont(c: Context, v: View) { typeface(c)?.let { font(v, it) } }
}

class SettingsActivity : AppCompatActivity() {
    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val f = File(filesDir, "font_${System.currentTimeMillis()}.ttf")
            contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            if (Typeface.Builder(f).build() == null) throw IllegalStateException()
            filesDir.listFiles()?.filter { it.name.startsWith("font_") && it != f }?.forEach { it.delete() }
            Style.prefs(this).edit().putString("font", f.absolutePath).apply(); recreate()
        } catch (e: Exception) { Toast.makeText(this, "That file isn't a usable font", Toast.LENGTH_SHORT).show() }
    }

    private fun row(parent: LinearLayout, title: String, click: () -> Unit): TextView {
        val v = layoutInflater.inflate(R.layout.row_setting, parent, false)
        v.findViewById<TextView>(R.id.title).text = title
        v.setOnClickListener { click() }
        parent.addView(v)
        return v.findViewById(R.id.value)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_settings)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val list = findViewById<LinearLayout>(R.id.list)
        val p = Style.prefs(this)
        fun pickOne(title: String, items: List<String>, key: String, after: () -> Unit = {}) {
            MaterialAlertDialogBuilder(this).setTitle(title).setSingleChoiceItems(items.toTypedArray(), p.getInt(key, 0)) { d, i ->
                p.edit().putInt(key, i).apply(); after(); d.dismiss(); recreate()
            }.show()
        }
        val themes = listOf("System default", "Light", "Dark")
        row(list, "Theme") { pickOne("Theme", themes, "theme") { AppCompatDelegate.setDefaultNightMode(Style.night(this)) } }.text = themes[p.getInt("theme", 0)]
        val cf = p.getString("font", "default") ?: "default"
        row(list, "Font") {
            MaterialAlertDialogBuilder(this).setTitle("Font").setSingleChoiceItems(Style.fontNames.toTypedArray(), Style.fontKeys.indexOf(cf).coerceAtLeast(0)) { d, i ->
                p.edit().putString("font", Style.fontKeys[i]).apply(); d.dismiss(); recreate()
            }.show()
        }.text = if (cf.startsWith("/")) "Custom font file" else Style.fontNames[Style.fontKeys.indexOf(cf).coerceAtLeast(0)]
        row(list, "Import font file…") { pick.launch(arrayOf("*/*")) }.text = "Use any .ttf or .otf from your phone"
        val units = listOf("Metric (km)", "Imperial (mi)")
        row(list, "Map units") { pickOne("Map units", units, "units") }.text = units[p.getInt("units", 0)]
        row(list, "Satellite place labels") { p.edit().putBoolean("labels", !p.getBoolean("labels", true)).apply(); recreate() }.text =
            if (p.getBoolean("labels", true)) "On" else "Off"
        row(list, "About") {}.text = "Rosewind 1.3 · Map data © OpenStreetMap contributors · Imagery © Esri · Photos from Wikimedia Commons"
        Style.refont(this, findViewById(android.R.id.content))
    }
}
