package com.example.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/** Hiển thị bảng điều khiển nổi + khung vùng click, và chạy vòng lặp click. */
class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var panel: LinearLayout
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var region: FrameLayout
    private lateinit var regionLp: WindowManager.LayoutParams
    private lateinit var etInterval: EditText
    private lateinit var btnStart: Button
    private lateinit var tvCount: TextView
    private lateinit var regionBg: GradientDrawable

    private var running = false
    private var intervalMs = 1000L
    private var clicks = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        buildRegion()
        buildPanel()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { wm.removeView(panel) }
        runCatching { wm.removeView(region) }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("autoclick", "Auto Click", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "autoclick")
            .setContentTitle("Auto Click đang chạy")
            .setContentText("Bảng điều khiển nổi đang hiển thị")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, n)
    }

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    // ---------------- Khung vùng click ----------------
    private fun buildRegion() {
        regionBg = GradientDrawable().apply {
            setColor(Color.argb(60, 255, 0, 0))
            setStroke(dp(2), Color.RED)
        }
        region = FrameLayout(this).apply { background = regionBg }
        region.addView(TextView(this).apply {
            text = "VÙNG CLICK\n(kéo để di chuyển)"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
        }, FrameLayout.LayoutParams(-1, -1))

        val handle = View(this).apply {
            setBackgroundColor(Color.RED)
        }
        region.addView(handle, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM or Gravity.END))

        regionLp = overlayParams(dp(240), dp(240)).apply {
            x = dp(100); y = dp(300)
        }

        // Kéo để di chuyển
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        region.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = regionLp.x; sy = regionLp.y }
                MotionEvent.ACTION_MOVE -> {
                    regionLp.x = sx + (e.rawX - dx).toInt()
                    regionLp.y = sy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(region, regionLp)
                }
            }
            true
        }
        // Kéo góc dưới phải để đổi kích thước
        var sw = 0; var sh = 0
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sw = regionLp.width; sh = regionLp.height }
                MotionEvent.ACTION_MOVE -> {
                    regionLp.width = (sw + (e.rawX - dx).toInt()).coerceAtLeast(dp(60))
                    regionLp.height = (sh + (e.rawY - dy).toInt()).coerceAtLeast(dp(60))
                    wm.updateViewLayout(region, regionLp)
                }
            }
            true
        }
        wm.addView(region, regionLp)
    }

    // ---------------- Bảng điều khiển ----------------
    private fun buildPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 30, 30, 30))
                cornerRadius = dp(14).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "≡ Auto Click (giữ để kéo)"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }
        panel.addView(title)

        // Hàng thời gian
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val minus = smallBtn("−") { setInterval(intervalMs - stepFor(intervalMs)) }
        val plus = smallBtn("+") { setInterval(intervalMs + stepFor(intervalMs)) }
        etInterval = EditText(this).apply {
            setText(intervalMs.toString())
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            minWidth = dp(70)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) setPanelFocusable(true)
                false
            }
            setOnEditorActionListener { v, _, _ ->
                applyInterval()
                setPanelFocusable(false)
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                true
            }
        }
        row.addView(minus)
        row.addView(etInterval)
        row.addView(TextView(this).apply { text = "ms"; setTextColor(Color.WHITE) })
        row.addView(plus)
        panel.addView(row)

        tvCount = TextView(this).apply {
            text = "Số lần: 0"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        panel.addView(tvCount)

        val row2 = LinearLayout(this)
        btnStart = Button(this).apply {
            text = "Bắt đầu"
            setOnClickListener { if (running) stopClicking() else startClicking() }
        }
        val close = Button(this).apply {
            text = "Đóng"
            setOnClickListener { stopSelf() }
        }
        row2.addView(btnStart, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row2)

        panelLp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            .apply { x = dp(20); y = dp(80) }

        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = panelLp.x; sy = panelLp.y }
                MotionEvent.ACTION_MOVE -> {
                    panelLp.x = sx + (e.rawX - dx).toInt()
                    panelLp.y = sy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(panel, panelLp)
                }
            }
            true
        }
        wm.addView(panel, panelLp)
    }

    private fun smallBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        minWidth = dp(44); minimumWidth = dp(44)
        setOnClickListener { onClick() }
    }

    private fun stepFor(v: Long) = when {
        v < 500 -> 50L
        v < 5000 -> 100L
        else -> 500L
    }

    private fun setInterval(v: Long) {
        intervalMs = v.coerceIn(MIN_INTERVAL, 3_600_000L)
        etInterval.setText(intervalMs.toString())
    }

    private fun applyInterval() {
        val v = etInterval.text.toString().toLongOrNull() ?: intervalMs
        setInterval(v)
    }

    private fun setPanelFocusable(focusable: Boolean) {
        panelLp.flags = if (focusable)
            panelLp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else
            panelLp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(panel, panelLp)
    }

    // ---------------- Vòng lặp click ----------------
    private fun startClicking() {
        if (AutoClickService.instance == null) {
            Toast.makeText(this, "Dịch vụ trợ năng chưa bật", Toast.LENGTH_LONG).show()
            return
        }
        applyInterval()
        setPanelFocusable(false)
        running = true
        clicks = 0
        btnStart.text = "Dừng"
        // Cho cú chạm xuyên qua khung vùng click
        regionLp.flags = regionLp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        regionBg.setColor(Color.argb(25, 255, 0, 0))
        wm.updateViewLayout(region, regionLp)
        handler.postDelayed(clickRunnable, 500)
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(clickRunnable)
        btnStart.text = "Bắt đầu"
        regionLp.flags = regionLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        regionBg.setColor(Color.argb(60, 255, 0, 0))
        wm.updateViewLayout(region, regionLp)
    }

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val svc = AutoClickService.instance
            if (svc == null) {
                Toast.makeText(this@FloatingService, "Mất dịch vụ trợ năng", Toast.LENGTH_SHORT).show()
                stopClicking(); return
            }
            val loc = IntArray(2)
            region.getLocationOnScreen(loc)
            val m = dp(6)
            val x = loc[0] + m + Random.nextFloat() * (region.width - 2 * m).coerceAtLeast(1)
            val y = loc[1] + m + Random.nextFloat() * (region.height - 2 * m).coerceAtLeast(1)
            svc.click(x, y)
            clicks++
            tvCount.text = "Số lần: $clicks"
            handler.postDelayed(this, intervalMs)
        }
    }

    companion object {
        const val MIN_INTERVAL = 50L
    }
}
