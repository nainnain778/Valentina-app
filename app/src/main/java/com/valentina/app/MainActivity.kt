package com.valentina.app
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val REQ_MEDIA = 1001

    inner class ScreenBridge {
        @JavascriptInterface
        fun getFrame(): String = ScreenCaptureService.latestFrameB64 ?: ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 100)
        val root = FrameLayout(this)
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            addJavascriptInterface(ScreenBridge(), "AndroidScreen")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val js = """
                        (function(){
                            if(window._hookedWS) return;
                            window._hookedWS = true;
                            const OrigWS = window.WebSocket;
                            window.WebSocket = function(u, p) {
                                const ws = p ? new OrigWS(u, p) : new OrigWS(u);
                                const origSend = ws.send.bind(ws);
                                ws.send = function(data) {
                                    try {
                                        if (typeof data === 'string' && window.AndroidScreen) {
                                            const frame = window.AndroidScreen.getFrame();
                                            if (frame && (data.includes('"text-input"') || data.includes('"mic-audio-end"'))) {
                                                const obj = JSON.parse(data);
                                                obj.images = [frame];
                                                data = JSON.stringify(obj);
                                            }
                                        }
                                    } catch(e) {}
                                    return origSend(data);
                                };
                                return ws;
                            };
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) { request.grant(request.resources) }
            }
            loadUrl("http://127.0.0.1:12393")
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnReload = Button(this).apply { text = "🔄"; alpha = 0.75f; setOnClickListener { webView.reload() } }
        val btnScreen = Button(this).apply {
            text = "📺 Ver Pantalla"
            alpha = 0.75f
            setOnClickListener {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_MEDIA)
            }
        }
        bar.addView(btnReload)
        bar.addView(btnScreen)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, 30, 30, 0)
        }
        root.addView(webView)
        root.addView(bar, params)
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("code", resultCode)
                putExtra("data", data)
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }
}
