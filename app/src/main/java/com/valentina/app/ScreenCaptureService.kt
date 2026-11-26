package com.valentina.app
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import okhttp3.*
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class ScreenCaptureService : Service() {
    private var running = true
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chId = "valentina_screen"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "Valentina Pantalla", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = Notification.Builder(this, chId).setContentTitle("Valentina viendo pantalla").setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, notif)
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(code, data)
        val reader = ImageReader.newInstance(720, 1280, PixelFormat.RGBA_8888, 2)
        projection.createVirtualDisplay("ValentinaScreen", 720, 1280, 320, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        val client = OkHttpClient()
        val ws = client.newWebSocket(Request.Builder().url("ws://localhost:12393/client-ws").build(), object : WebSocketListener() {})
        thread {
            while (running) {
                Thread.sleep(2500)
                val img = reader.acquireLatestImage() ?: continue
                val plane = img.planes[0][0][0]
                val bmp = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, 1280, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(plane.buffer)
                img.close()
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 55, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                ws.send("""{"type":"mic-audio-data","images":["data:image/jpeg;base64,$b64"]}""")
            }
        }
        return START_STICKY
    }
    override fun onDestroy() { running = false; super.onDestroy() }
}
