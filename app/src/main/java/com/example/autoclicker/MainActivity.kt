package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "Auto Click"
            textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "\nBước 1: Cấp quyền hiển thị trên ứng dụng khác.\n" +
                "Bước 2: Bật dịch vụ trợ năng \"Auto Click\".\n" +
                "Bước 3: Mở bảng điều khiển nổi, kéo khung đỏ tới vùng cần click, chỉnh thời gian rồi bấm Bắt đầu.\n"
        })
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }.also { root.addView(it) }

        btn("1. Cấp quyền hiển thị nổi") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        btn("2. Bật dịch vụ trợ năng") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("3. Mở bảng điều khiển nổi") {
            when {
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, "Hãy cấp quyền hiển thị nổi trước", Toast.LENGTH_LONG).show()
                !isAccessibilityOn() ->
                    Toast.makeText(this, "Hãy bật dịch vụ trợ năng trước", Toast.LENGTH_LONG).show()
                else -> {
                    startForegroundService(Intent(this, FloatingService::class.java))
                    moveTaskToBack(true)
                }
            }
        }
        btn("Tắt bảng điều khiển nổi") {
            stopService(Intent(this, FloatingService::class.java))
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = "Quyền nổi: " + (if (Settings.canDrawOverlays(this)) "✅" else "❌") +
            "    Trợ năng: " + (if (isAccessibilityOn()) "✅" else "❌")
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }
}
