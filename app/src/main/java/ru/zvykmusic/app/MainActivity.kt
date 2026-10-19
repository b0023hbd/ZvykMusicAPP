package ru.zvykmusic.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var webContainer: LinearLayout
    private lateinit var bottom: LinearLayout
    private var web: WebView? = null
    private var errorView: LinearLayout? = null

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
        val appearanceButton = Button(this).apply {
            text = "🎨"
            contentDescription = "Настроить оформление"
            setTextColor(Color.WHITE)
            setBackgroundColor(surface)
            setOnClickListener { showAppearanceDialog() }
        }
        header.addView(appearanceButton, LinearLayout.LayoutParams(dp(52), dp(44)))
        root.addView(header)

        webContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))

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

        // Attach the view hierarchy before starting WebView navigation.
        setContentView(root)
        styleTabs()
        createWebView(SiteConfig.HOME)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(url: String) {
        errorView?.let { webContainer.removeView(it) }
        errorView = null
        web?.let { oldWeb ->
            webContainer.removeView(oldWeb)
            oldWeb.stopLoading()
            oldWeb.destroy()
        }
        web = null

        val newWeb = WebView(this)
        web = newWeb
        newWeb.setBackgroundColor(bg)
        newWeb.isFocusable = true
        newWeb.isFocusableInTouchMode = true
        newWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(newWeb, true)
        newWeb.webChromeClient = WebChromeClient()
        newWeb.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty().lowercase()
                if (uri.scheme == "https" && (host == "web.zvykmusic.ru" || host == "zvykmusic.ru" || host.endsWith(".zvykmusic.ru"))) {
                    return false
                }
                if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto" || uri.scheme == "tel") {
                    return openExternal(uri)
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Log.e("Zvykmusic", "WebView load error ${error.errorCode}: ${error.description}")
                    showLoadError()
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                    Log.e("Zvykmusic", "HTTP ${errorResponse.statusCode} loading ${request.url}")
                    showLoadError()
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                Log.e("Zvykmusic", "WebView renderer exited; crashed=${detail.didCrash()}")
                if (web === view) {
                    webContainer.removeView(view)
                    view.destroy()
                    web = null
                    showLoadError()
                } else {
                    view.destroy()
                }
                // Required by Android to prevent renderer termination from crashing the Activity.
                return true
            }
        }
        webContainer.addView(newWeb, LinearLayout.LayoutParams(-1, -1))
        newWeb.loadUrl(url)
    }

    private fun showLoadError() {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (errorView?.parent != null) return@runOnUiThread
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(bg)
            }
            panel.addView(TextView(this).apply {
                text = "Не удалось открыть Zvykmusic"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1, -2))
            panel.addView(TextView(this).apply {
                text = "Проверь подключение к интернету и попробуй ещё раз."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(10), 0, dp(16))
            }, LinearLayout.LayoutParams(-1, -2))
            panel.addView(Button(this).apply {
                text = "Повторить"
                setOnClickListener { createWebView(SiteConfig.HOME) }
            })
            errorView = panel
            webContainer.addView(panel, LinearLayout.LayoutParams(-1, -1))
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Нет приложения для открытия этой ссылки", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e("Zvykmusic", "Could not open external URL: $uri", e)
            true
        }
    }

    private fun addTab(label: String, url: String) {
        val button = TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(5), dp(2), dp(5))
            setOnClickListener {
                val currentWeb = web
                if (currentWeb == null) {
                    createWebView(url)
                } else if (url == SiteConfig.SEARCH) {
                    currentWeb.evaluateJavascript(
                        "(function(){var e=document.querySelector('input[type=search],input[placeholder*=Поиск],input[placeholder*=поиск]'); if(e){e.focus();return 'focused';} location.href='${url}'; return 'navigate';})()",
                        null
                    )
                } else {
                    currentWeb.loadUrl(url)
                }
            }
        }
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
        val currentWeb = web
        if (currentWeb != null && currentWeb.canGoBack()) currentWeb.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        web?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        web = null
        super.onDestroy()
    }
}
