package com.emobox.sendtest

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.abs

/**
 * 시험 4: 카톡 위에 떠 있는 버튼.
 * 버튼 → 작은 창 → 그림 탭 = 복사(붙여넣기용), 길게 누르기 = 카톡으로 공유
 */
class BubbleService : Service() {

    companion object {
        const val STOP = "stop"
        var running = false
    }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        wm = getSystemService(WindowManager::class.java)
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        closePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("bubble", "떠 있는 버튼", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "bubble")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("이모 떠 있는 버튼 켜짐")
            .setContentText("이 알림을 누르면 꺼져요")
            .setContentIntent(stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
    }

    private fun overlayParams(w: Int, h: Int, extraFlags: Int = 0) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or extraFlags,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val size = dp(58)
        val v = TextView(this).apply {
            text = "이모"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF5C7A"))
                setStroke(dp(3), Color.WHITE)
            }
            elevation = dp(6).toFloat()
        }
        bubbleLp = overlayParams(size, size).apply {
            x = resources.displayMetrics.widthPixels - size - dp(8)
            y = resources.displayMetrics.heightPixels / 3
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = bubbleLp.x; startY = bubbleLp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        bubbleLp.x = startX + dx.toInt(); bubbleLp.y = startY + dy.toInt()
                        wm.updateViewLayout(v, bubbleLp)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(v, bubbleLp)
        bubble = v
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        EmoStore.ensureSamples(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#DFE3F1"))
            }
            elevation = dp(10).toFloat()
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "탭: 복사 → 입력창 길게 눌러 붙여넣기\n길게: 카톡으로 공유"
            textSize = 12.5f
            setTextColor(Color.parseColor("#1C2240"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = "✕"; textSize = 20f; setPadding(dp(12), 0, dp(4), 0)
            setTextColor(Color.parseColor("#666D8F"))
            setOnClickListener { closePanel() }
        })
        val row = LinearLayout(this)
        EmoStore.list(this).forEach { f -> row.addView(tile(f), LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginEnd = dp(8) }) }
        box.addView(head)
        box.addView(HorizontalScrollView(this).apply { addView(row) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val w = resources.displayMetrics.widthPixels - dp(24)
        val lp = overlayParams(w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).apply {
            x = dp(12)
            y = (bubbleLp.y + dp(66)).coerceAtMost(resources.displayMetrics.heightPixels - dp(200))
        }
        box.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel(); false
        }
        wm.addView(box, lp)
        panel = box
    }

    private fun tile(f: File) = ImageView(this).apply {
        setImageBitmap(EmoStore.thumb(f, dp(80)))
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = GradientDrawable().apply { setColor(Color.parseColor("#EEF1F9")); cornerRadius = dp(14).toFloat() }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        contentDescription = f.name
        setOnClickListener {
            EmoStore.copyToClipboard(this@BubbleService, f)
            EmoStore.put(this@BubbleService, "bubble_last", "복사: ${f.name}")
            closePanel()
            Toast.makeText(this@BubbleService, "복사했어요. 카톡 입력창을 길게 눌러 ‘붙여넣기’", Toast.LENGTH_LONG).show()
        }
        setOnLongClickListener {
            closePanel()
            EmoStore.put(this@BubbleService, "bubble_last", "공유: ${f.name}")
            try {
                startActivity(EmoStore.shareIntent(this@BubbleService, f, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this@BubbleService, "카카오톡을 열 수 없어요: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
            true
        }
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }
}
