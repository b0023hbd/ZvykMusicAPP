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

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Keep the real website as the source of truth; enhance its presentation in-app.
                view.evaluateJavascript(GLASS_ENHANCEMENT_SCRIPT, null)
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

    private val GLASS_ENHANCEMENT_SCRIPT = """
        (function() {
          if (window.__zvykGlassInstalled) return;
          window.__zvykGlassInstalled = true;
          var style = document.createElement('style');
          style.id = 'zvyk-glass-theme';
          style.textContent = `
            :root { color-scheme: dark; --zvyk-accent: #a78bfa; --zvyk-glass: rgba(255,255,255,.075); --zvyk-line: rgba(255,255,255,.12); }
            html { background: #090910 !important; }
            body { background: radial-gradient(ellipse at 15% 0%, rgba(124,58,237,.20), transparent 42%), radial-gradient(ellipse at 100% 35%, rgba(14,165,233,.10), transparent 35%), #090910 !important; color: #f6f4ff !important; }
            header, nav, [role=navigation], .navbar, .nav, .topbar, .bottom-nav, .sidebar { background: rgba(18,17,30,.72) !important; backdrop-filter: blur(24px) saturate(160%) !important; -webkit-backdrop-filter: blur(24px) saturate(160%) !important; border-color: var(--zvyk-line) !important; }
            article, .card, [class*=card], [class*=track], [class*=album], [class*=playlist], [class*=artist], [class*=player], [class*=panel], [class*=tile] { border-color: var(--zvyk-line) !important; }
            .card, [class*=card], [class*=track], [class*=album], [class*=playlist], [class*=artist], [class*=player], [class*=panel], [class*=tile] { background-color: rgba(255,255,255,.055) !important; border-radius: 18px !important; box-shadow: 0 8px 30px rgba(0,0,0,.14); }
            button, [role=button], input, select, textarea { border-radius: 13px !important; }
            button, [role=button] { transition: transform .16s ease, filter .16s ease; }
            button:active, [role=button]:active { transform: scale(.97); }
            input, select, textarea { background: rgba(255,255,255,.07) !important; color: #fff !important; border: 1px solid var(--zvyk-line) !important; }
            a { color: #c4b5fd; }
            img { border-radius: 12px; }
            ::-webkit-scrollbar { width: 4px; height: 4px; } ::-webkit-scrollbar-thumb { background: rgba(167,139,250,.48); border-radius: 10px; }
            .zvyk-wave { height: 46px; margin: 12px 0 16px; padding: 7px 10px; display:flex; align-items:center; gap:3px; overflow:hidden; border:1px solid rgba(255,255,255,.12); border-radius:16px; background:linear-gradient(115deg,rgba(139,92,246,.15),rgba(56,189,248,.08)); box-sizing:border-box; }
            .zvyk-wave canvas { width:100%; height:100%; display:block; }
          `;
          (document.head || document.documentElement).appendChild(style);
          function attachWave(audio) {
            if (!audio || audio.dataset.zvykWave) return;
            audio.dataset.zvykWave = '1';
            var wrap = document.createElement('div'); wrap.className = 'zvyk-wave'; wrap.setAttribute('aria-label','Волна воспроизведения');
            var canvas = document.createElement('canvas'); wrap.appendChild(canvas);
            if (audio.parentNode) audio.parentNode.insertBefore(wrap, audio.nextSibling);
            var ctx = canvas.getContext('2d'); var bars = []; var count = 76;
            for (var i=0;i<count;i++) { var v = Math.abs(Math.sin(i*12.9898+4.1415)*43758.5453)%1; bars.push(.16 + v*.84); }
            function draw() {
              if (!canvas.isConnected) return;
              var rect = wrap.getBoundingClientRect(); var dpr = Math.min(window.devicePixelRatio||1,2);
              var w = Math.max(1,Math.floor(rect.width*dpr)), h = Math.max(1,Math.floor(rect.height*dpr));
              if (canvas.width!==w || canvas.height!==h) { canvas.width=w; canvas.height=h; }
              ctx.clearRect(0,0,w,h); var gap=3*dpr, bw=(w-gap*(count-1))/count; var progress=audio.duration>0?audio.currentTime/audio.duration:0;
              for (var i=0;i<count;i++) { var bh=Math.max(3*dpr,bars[i]*(h*.88)); var x=i*(bw+gap), y=(h-bh)/2; var played=(i/count)<=progress; var grad=ctx.createLinearGradient(0,y,0,y+bh); grad.addColorStop(0,played?'#c4b5fd':'rgba(196,181,253,.52)'); grad.addColorStop(1,played?'#38bdf8':'rgba(56,189,248,.22)'); ctx.fillStyle=grad; ctx.beginPath(); if(ctx.roundRect) ctx.roundRect(x,y,bw,bh,bw/2); else ctx.rect(x,y,bw,bh); ctx.fill(); }
              if (!audio.paused && !audio.ended) requestAnimationFrame(draw);
            }
            wrap.addEventListener('click',function(e){ if(audio.duration>0){var r=wrap.getBoundingClientRect();audio.currentTime=Math.max(0,Math.min(1,(e.clientX-r.left)/r.width))*audio.duration;draw();} });
            ['play','pause','timeupdate','durationchange','seeked','ended'].forEach(function(ev){audio.addEventListener(ev,draw);});
            if ('ResizeObserver' in window) new ResizeObserver(draw).observe(wrap); else window.addEventListener('resize',draw);
            draw();
          }
          function scan() { document.querySelectorAll('audio').forEach(attachWave); }
          scan();
          new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});
        })();
    """.trimIndent()

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
