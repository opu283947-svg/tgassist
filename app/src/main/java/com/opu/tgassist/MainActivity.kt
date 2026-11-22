package com.opu.tgassist

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var power: Button
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12081c"))
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "TG Assistant"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title)

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#c9b8e0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        root.addView(status)

        power = Button(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onPower() }
        }
        val pLp = LinearLayout.LayoutParams(340, 340).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 20
            bottomMargin = 30
        }
        root.addView(power, pLp)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val copy = Button(this).apply {
            text = "লগ কপি"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", Bot.dump()))
                Toast.makeText(this@MainActivity, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
        val clear = Button(this).apply {
            text = "লগ মুছুন"
            setOnClickListener { Bot.clear() }
        }
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(clear, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)

        scroll = ScrollView(this)
        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#9fe8b8"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(10, 10, 10, 10)
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName + "/")
    }

    private fun onPower() {
        if (!accessibilityOn()) {
            Toast.makeText(this, "Accessibility চালু করুন: TG Assistant বেছে নিন", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        Bot.enabled = !Bot.enabled
        Bot.log(if (Bot.enabled) "▶ অ্যাসিস্ট্যান্ট চালু" else "■ অ্যাসিস্ট্যান্ট বন্ধ")
        refresh()
    }

    private fun refresh() {
        val acc = accessibilityOn()
        status.text = when {
            !acc -> "Accessibility বন্ধ। বাটনে চাপুন, সেটিংসে গিয়ে চালু করুন"
            Bot.enabled -> "চালু আছে। এখন Telegram খুলুন"
            else -> "প্রস্তুত। বাটন চাপলে শুরু হবে"
        }
        power.text = if (Bot.enabled) "ON" else "OFF"
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (Bot.enabled) "#1fa85a" else "#c0345a"))
        }
        power.background = bg
        val txt = Bot.dump()
        if (txt != logView.text.toString()) {
            logView.text = txt
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
