package ru.zvykmusic.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var bottom: LinearLayout
    private val bg = Color.rgb(11, 11, 18)
    private val surface = Color.rgb(23, 23, 34)
    private val prefs by lazy { getSharedPreferences("zvyk_settings", MODE_PRIVATE) }
    private var accent = Color.rgb(139, 92, 246)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = prefs.getInt("accent", Color.rgb(139, 92, 246))
        window.statusBarColor = bg
        window.navigationBarColor = bg

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            setBackgroundColor(bg)
        }
        val brand = TextView(this).apply {
            text = "Zvykmusic"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        val settings = Button(this).apply {
            text = "🎨"
            contentDescription = "Настроить оформление"
            setTextColor(Color.WHITE)
            setBackgroundColor(surface)
            setOnClickListener { showAppearanceDialog() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(52), dp(44)))
        root.addView(header)

        web = WebView(this).apply {
            setBackgroundColor(bg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && uri.host == "web.zvykmusic.ru") return false
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        return true
                    }
                    return true
                }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            loadUrl(SiteConfig.HOME)
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(surface)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        addTab("⌂\nГлавная", SiteConfig.HOME)
        addTab("⌕\nПоиск", SiteConfig.SEARCH)
        addTab("♫\nПопулярное", SiteConfig.POPULAR)
        addTab("♬\nАртисты", SiteConfig.ARTISTS)
        addTab("☺\nПрофиль", SiteConfig.PROFILE)
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(62)))
        setContentView(root)
        styleTabs()
    }

    private fun addTab(label: String, url: String) {
        val button = TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(5), dp(2), dp(5))
            setOnClickListener {
                if (url == SiteConfig.SEARCH) {
                    web.evaluateJavascript(
                        "(function(){var e=document.querySelector('input[type=search],input[placeholder*=Поиск],input[placeholder*=поиск]'); if(e){e.focus();return 'focused';} location.href='${url}'; return 'navigate';})()",
                        null
                    )
                } else web.loadUrl(url)
            }
        }
        button.tag = "tab"
        bottom.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun styleTabs() {
        for (i in 0 until bottom.childCount) {
            (bottom.getChildAt(i) as? TextView)?.setTextColor(if (i == 0) accent else Color.LTGRAY)
        }
    }

    private fun showAppearanceDialog() {
        val names = arrayOf("Фиолетовый", "Голубой", "Розовый", "Зелёный", "Оранжевый")
        val colors = intArrayOf(
            Color.rgb(139, 92, 246), Color.rgb(56, 189, 248),
            Color.rgb(244, 114, 182), Color.rgb(52, 211, 153), Color.rgb(251, 146, 60)
        )
        val selected = colors.indexOf(accent).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Оформление Zvykmusic")
            .setSingleChoiceItems(names, selected) { dialog, which ->
                accent = colors[which]
                prefs.edit().putInt("accent", accent).apply()
                styleTabs()
                Toast.makeText(this, "Цвет сохранён", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
