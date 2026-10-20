package ru.zvykmusic.app

import android.annotation.SuppressLint
import android.app.Activity
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.webkit.JavascriptInterface
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
    private lateinit var trackTitle: TextView
    private lateinit var trackSubtitle: TextView
    private lateinit var playPauseButton: TextView
    private var mediaSession: MediaSession? = null
    private var isPlaying = false
    private var currentTitle = "Zvykmusic"
    private var currentArtist = "Выберите трек на сайте"
    private var durationMs = 0L
    private var positionMs = 0L

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
        setupMediaSession()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(220, 20, 20, 31))
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

        val miniPlayer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundColor(Color.argb(224, 31, 29, 46))
        }
        val trackText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        trackTitle = TextView(this).apply { text = currentTitle; textSize = 13f; setTextColor(Color.WHITE); maxLines = 1; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        trackSubtitle = TextView(this).apply { text = currentArtist; textSize = 11f; setTextColor(Color.LTGRAY); maxLines = 1 }
        trackText.addView(trackTitle)
        trackText.addView(trackSubtitle)
        miniPlayer.addView(trackText, LinearLayout.LayoutParams(0, dp(42), 1f))
        val backButton = TextView(this).apply { text = "−10"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { seekBy(-10000) } }
        miniPlayer.addView(backButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        playPauseButton = TextView(this).apply { text = "▶"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { togglePlayback() } }
        miniPlayer.addView(playPauseButton, LinearLayout.LayoutParams(dp(46), dp(42)))
        val forwardButton = TextView(this).apply { text = "+10"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { seekBy(10000) } }
        miniPlayer.addView(forwardButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(miniPlayer, LinearLayout.LayoutParams(-1, dp(58)))

        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(236, 24, 23, 36))
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
        newWeb.addJavascriptInterface(MediaBridge(), "ZvykNativeMedia")
        newWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectMediaBridge(view)
            }

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

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "ZvykmusicPlayer").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { runOnUiThread { controlWebMedia("play"); isPlaying = true; updateMediaState() } }
                override fun onPause() { runOnUiThread { controlWebMedia("pause"); isPlaying = false; updateMediaState() } }
                override fun onSkipToNext() { runOnUiThread { clickSiteTrackButton(true) } }
                override fun onSkipToPrevious() { runOnUiThread { clickSiteTrackButton(false) } }
                override fun onSeekTo(pos: Long) { runOnUiThread { seekTo(pos) } }
            })
            isActive = true
        }
        updateMediaState()
    }

    private inner class MediaBridge {
        @JavascriptInterface fun onTrack(title: String?, artist: String?, duration: Double, position: Double, playing: Boolean) {
            runOnUiThread {
                currentTitle = title?.takeIf { it.isNotBlank() } ?: "Zvykmusic"
                currentArtist = artist?.takeIf { it.isNotBlank() } ?: "Воспроизведение на сайте"
                durationMs = (duration * 1000).toLong().coerceAtLeast(0)
                positionMs = (position * 1000).toLong().coerceAtLeast(0)
                isPlaying = playing
                trackTitle.text = currentTitle
                trackSubtitle.text = currentArtist
                playPauseButton.text = if (isPlaying) "Ⅱ" else "▶"
                updateMediaState()
            }
        }
    }

    private fun injectMediaBridge(view: WebView) {
        val js = """(function(){
          if(window.__zvykMediaHooked)return; window.__zvykMediaHooked=true;
          function media(){return Array.from(document.querySelectorAll('audio,video')).find(x=>!x.paused&&!x.ended)||document.querySelector('audio,video');}
          function report(){var m=media(); if(!m)return; var title=document.querySelector('meta[property="og:title"]')?.content||document.title||'Zvykmusic'; var artist=document.querySelector('[class*=artist i]')?.innerText||'Воспроизведение на сайте'; try{ZvykNativeMedia.onTrack(title,artist,m.duration||0,m.currentTime||0,!m.paused&&!m.ended)}catch(e){}}
          document.addEventListener('play',report,true);document.addEventListener('pause',report,true);document.addEventListener('timeupdate',report,true);document.addEventListener('loadedmetadata',report,true);setInterval(report,2000);
          window.__zvykControl=function(action,value){var m=media();if(!m)return false;try{if(action==='play')m.play();else if(action==='pause')m.pause();else if(action==='seek')m.currentTime=Math.max(0,Math.min(m.duration||1,value/1000));return true}catch(e){return false}};
          window.__zvykSkip=function(next){var sels=next?['[aria-label*=next i]','button[title*=next i]','button[class*=next i]','[data-testid*=next i]']:['[aria-label*=previous i]','[aria-label*=prev i]','button[title*=previous i]','button[class*=prev i]','[data-testid*=previous i]'];for(var s of sels){var e=document.querySelector(s);if(e){e.click();return true}}return false}; report();
        })();"""
        view.evaluateJavascript(js, null)
    }

    private fun controlWebMedia(action: String) {
        web?.evaluateJavascript("window.__zvykControl && window.__zvykControl('$action',0)", null)
    }

    private fun seekBy(delta: Long) { seekTo(positionMs + delta) }
    private fun seekTo(position: Long) {
        positionMs = position.coerceAtLeast(0).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }
        web?.evaluateJavascript("window.__zvykControl && window.__zvykControl('seek',$positionMs)", null)
        updateMediaState()
    }
    private fun togglePlayback() { controlWebMedia(if (isPlaying) "pause" else "play"); isPlaying = !isPlaying; updateMediaState() }
    private fun clickSiteTrackButton(next: Boolean) {
        web?.evaluateJavascript("window.__zvykSkip && window.__zvykSkip($next)", null)
    }
    private fun updateMediaState() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
        mediaSession?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle).putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist).putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs).build())
        mediaSession?.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, positionMs, 1f).build())
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
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
