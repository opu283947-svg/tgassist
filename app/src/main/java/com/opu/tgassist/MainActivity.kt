package com.opu.tgassist

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PowerIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var tint = Color.WHITE
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.09f
        p.strokeCap = Paint.Cap.ROUND
        p.color = tint
        c.drawArc(RectF(w * 0.16f, h * 0.2f, w * 0.84f, h * 0.88f), -50f, 280f, false, p)
        c.drawLine(w * 0.5f, h * 0.08f, w * 0.5f, h * 0.5f, p)
    }
}

class MainActivity : Activity() {

    private val RED = "#ff3b30"
    private val AMB = "#ffb300"
    private val CARD = "#cc2a0e0b"
    private val BORDER = "#6b2a1f"
    private val MUTED = "#b9a8a2"

    private val handler = Handler(Looper.getMainLooper())
    private val panels = ArrayList<View>()
    private val navs = ArrayList<TextView>()
    private var tab = 0

    private lateinit var status: TextView
    private lateinit var core: LinearLayout
    private lateinit var powerIcon: PowerIcon
    private lateinit var powerText: TextView
    private lateinit var chipPages: TextView
    private lateinit var chipJob: TextView
    private lateinit var chipTg: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var aiRequest: EditText
    private lateinit var aiReply: EditText
    private lateinit var docView: TextView
    private lateinit var docScroll: ScrollView
    private lateinit var codeBox: EditText
    private lateinit var autoBtn: Button
    private var lastMemLen = -1L
    private var pageCount = 0

    private val sample = """# স্ক্রিপ্ট: # দিয়ে শুরু লাইন মন্তব্য
LOG "পরীক্ষা শুরু"
IF_TEXT "Open CherryPlay"
  TAP_TEXT "Open CherryPlay"
  WAIT 4
END
WAIT_TEXT "Games" 15
TAP_TEXT "Games" last
WAIT 3
SWIPE_UP
SWIPE_DOWN
LOG "পরীক্ষা শেষ"
"""

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    // ---------- ছোট সাহায্যকারী ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: String, radius: Int, stroke: String?): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(color))
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke))
        return g
    }

    private fun lp(w: Int, h: Int, weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    private fun pill(label: String, color: String, size: Float, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.textSize = size
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(6), dp(10), dp(6), dp(10))
        b.background = shape(color, 14, null)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun row(vararg btns: Button): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (b in btns) {
            val l = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(3), dp(3), dp(3), dp(3))
            r.addView(b, l)
        }
        return r
    }

    private fun caption(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.parseColor("#ffd9cf"))
        t.textSize = 12f
        t.setPadding(dp(4), dp(6), 0, dp(4))
        return t
    }

    private fun edit(hint: String): EditText {
        val e = EditText(this)
        e.setTextColor(Color.parseColor("#fff1e0"))
        e.setHintTextColor(Color.parseColor("#8a6f66"))
        e.hint = hint
        e.typeface = Typeface.MONOSPACE
        e.textSize = 12f
        e.gravity = Gravity.TOP
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.background = shape("#140706", 14, BORDER)
        e.setPadding(dp(10), dp(8), dp(10), dp(8))
        return e
    }

    private fun chip(): TextView {
        val t = TextView(this)
        t.setTextColor(Color.parseColor(AMB))
        t.textSize = 15f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.background = shape(CARD, 16, BORDER)
        t.setPadding(dp(6), dp(12), dp(6), dp(12))
        return t
    }

    // ---------- ইউআই তৈরি ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#3a0d09"), Color.parseColor("#150504"), Color.parseColor("#0a0303"))
        )

        // হেডার
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(18), dp(34), dp(18), dp(6))
        val title = TextView(this)
        title.text = "TG Assistant"
        title.setTextColor(Color.parseColor("#fff4ee"))
        title.textSize = 21f
        title.typeface = Typeface.DEFAULT_BOLD
        head.addView(title, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tgChip = TextView(this)
        tgChip.text = "Telegram"
        tgChip.setTextColor(Color.parseColor(AMB))
        tgChip.textSize = 11f
        tgChip.background = shape("#00000000", 20, AMB)
        tgChip.setPadding(dp(10), dp(4), dp(10), dp(4))
        head.addView(tgChip)
        root.addView(head)

        val content = FrameLayout(this)
        content.setPadding(dp(14), dp(4), dp(14), dp(4))
        panels.add(buildHome())
        panels.add(buildAi())
        panels.add(buildMemory())
        for (p in panels) content.addView(p)
        root.addView(content, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // নিচের নেভ
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#1a0605"))
        val names = listOf("⌂\nHome", "✦\nAI", "▤\nMemory")
        for (i in 0 until 3) {
            val t = TextView(this)
            t.text = names[i]
            t.gravity = Gravity.CENTER
            t.textSize = 12f
            t.typeface = Typeface.DEFAULT_BOLD
            t.setPadding(0, dp(10), 0, dp(12))
            t.setOnClickListener { showTab(i) }
            navs.add(t)
            nav.addView(t, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(nav)

        setContentView(root)
        showTab(0)
    }

    private fun buildHome(): View {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.gravity = Gravity.CENTER_HORIZONTAL

        val frame = FrameLayout(this)
        val glow2 = View(this)
        glow2.background = ring("#1fff6a1a")
        val glow1 = View(this)
        glow1.background = ring("#33ff6a1a")
        frame.addView(glow2, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
        frame.addView(glow1, FrameLayout.LayoutParams(dp(164), dp(164), Gravity.CENTER))
        core = LinearLayout(this)
        core.orientation = LinearLayout.VERTICAL
        core.gravity = Gravity.CENTER
        powerIcon = PowerIcon(this)
        core.addView(powerIcon, LinearLayout.LayoutParams(dp(50), dp(50)))
        powerText = TextView(this)
        powerText.textSize = 18f
        powerText.typeface = Typeface.DEFAULT_BOLD
        powerText.gravity = Gravity.CENTER
        core.addView(powerText)
        core.setOnClickListener { onPower() }
        frame.addView(core, FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER))
        val fl = LinearLayout.LayoutParams(dp(180), dp(180))
        fl.topMargin = dp(8)
        p.addView(frame, fl)

        status = TextView(this)
        status.setTextColor(Color.parseColor("#ffd9cf"))
        status.textSize = 13f
        status.gravity = Gravity.CENTER
        status.background = shape(CARD, 30, BORDER)
        status.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sl = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sl.topMargin = dp(6)
        sl.bottomMargin = dp(10)
        p.addView(status, sl)

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chipPages = chip()
        chipJob = chip()
        chipTg = chip()
        for (c in listOf(chipPages, chipJob, chipTg)) {
            val l = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(4), 0, dp(4), 0)
            chips.addView(c, l)
        }
        p.addView(chips, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f))

        logScroll = ScrollView(this)
        logScroll.background = shape(CARD, 18, BORDER)
        logView = TextView(this)
        logView.setTextColor(Color.parseColor("#ffd27a"))
        logView.textSize = 11f
        logView.typeface = Typeface.MONOSPACE
        logView.setPadding(dp(10), dp(10), dp(10), dp(10))
        logScroll.addView(logView)
        val ll = lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        ll.topMargin = dp(10)
        p.addView(logScroll, ll)

        p.addView(row(
            pill("লগ কপি", "#4a1a14", 12f) { copyText(Bot.dump(), "লগ") },
            pill("লগ মুছুন", "#4a1a14", 12f) { Bot.clear() }
        ))
        return p
    }

    private fun ring(color: String): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.parseColor(color))
        return g
    }

    private fun buildAi(): View {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        p.addView(caption("✦ আমি কী চাই (বাংলায় লিখুন)"))
        aiRequest = edit("যেমন: Tasks পেজের Daily ট্যাবের Watch বাটনগুলো একে একে ট্যাপ করো")
        p.addView(aiRequest, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(90), 0f))
        p.addView(row(
            pill("📦 Claude-এর জন্য প্যাকেজ কপি", "#c4161c", 13f) { copyPackage() }
        ))
        val hint = TextView(this)
        hint.text = "কপি করে Claude.ai চ্যাটে পেস্ট করুন। উত্তরে স্ক্রিপ্ট পাবেন, সেটা নিচে বসান।"
        hint.setTextColor(Color.parseColor(MUTED))
        hint.textSize = 11f
        hint.setPadding(dp(4), 0, dp(4), dp(4))
        p.addView(hint)

        p.addView(caption("↓ Claude-এর উত্তর (স্ক্রিপ্ট) এখানে পেস্ট করুন"))
        aiReply = edit("স্ক্রিপ্ট পেস্ট করুন")
        p.addView(aiReply, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        p.addView(row(
            pill("▶ সরাসরি চালান", "#1f9d57", 13f) { runCode(aiReply.text.toString()) },
            pill("→ Memory-র কোড বক্সে", "#8a2a14", 13f) {
                codeBox.setText(aiReply.text.toString())
                showTab(2)
            }
        ))
        return p
    }

    private fun buildMemory(): View {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        p.addView(caption("▤ মেমরি ডকুমেন্ট (যা যা শিখেছে + সেভ করা স্ক্রিপ্ট)"))
        docScroll = ScrollView(this)
        docScroll.background = shape(CARD, 16, BORDER)
        docView = TextView(this)
        docView.setTextColor(Color.parseColor("#ffd27a"))
        docView.textSize = 10.5f
        docView.typeface = Typeface.MONOSPACE
        docView.setTextIsSelectable(true)
        docView.setPadding(dp(10), dp(10), dp(10), dp(10))
        docScroll.addView(docView)
        p.addView(docScroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        p.addView(row(
            pill("🎓 শেখা ৩ মি.", "#8a2a14", 12f) { startLearn() },
            pill("মেমরি কপি", "#4a1a14", 12f) { copyText(memoryDoc(150000), "মেমরি") },
            pill("মেমরি রিসেট", "#4a1a14", 12f) {
                Store.write(this, "memory.txt", "")
                lastMemLen = -1L
                Toast.makeText(this, "মেমরি খালি করা হলো", Toast.LENGTH_SHORT).show()
            }
        ))

        p.addView(caption("</> কোড বক্স (লিখে Run দিলে সাথে সাথে কাজ বদলায়)"))
        codeBox = edit("TAP_TEXT \"Games\"")
        val saved = Store.read(this, "playbook.txt")
        codeBox.setText(if (saved.isBlank()) sample else saved)
        p.addView(codeBox, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(150), 0f))

        autoBtn = pill("অটো-রান: বন্ধ", "#4a1a14", 11f) { toggleAuto() }
        p.addView(row(
            pill("▶ চালান", "#1f9d57", 13f) { runCode(codeBox.text.toString()) },
            pill("⏹ থামান", "#b8344f", 13f) { stopJob() },
            pill("💾 সেভ", "#8a2a14", 13f) { saveCode() },
            autoBtn
        ))
        return p
    }

    // ---------- ট্যাব ----------

    private fun showTab(i: Int) {
        tab = i
        for (k in 0 until panels.size) {
            panels[k].visibility = if (k == i) View.VISIBLE else View.GONE
            navs[k].setTextColor(Color.parseColor(if (k == i) AMB else "#a99a95"))
        }
        lastMemLen = -1L
        refresh()
    }

    // ---------- কাজ ----------

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName + "/")
    }

    private fun autoOn(): Boolean = Store.read(this, "autorun.txt").trim() == "1"

    private fun toggleAuto() {
        Store.write(this, "autorun.txt", if (autoOn()) "0" else "1")
        refresh()
    }

    private fun onPower() {
        if (!accessibilityOn()) {
            Toast.makeText(this, "Accessibility চালু করুন: TG Assistant বেছে নিন", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        Bot.enabled = !Bot.enabled
        if (!Bot.enabled) Bot.cancel = true
        Bot.log(if (Bot.enabled) "▶ অ্যাসিস্ট্যান্ট চালু" else "■ অ্যাসিস্ট্যান্ট বন্ধ")
        if (Bot.enabled && autoOn()) {
            val code = Store.read(this, "playbook.txt")
            if (code.isNotBlank()) {
                Bot.submit("script", code)
                Bot.log("⚙ অটো-রান: সেভ করা স্ক্রিপ্ট চলবে। Telegram-এ যান")
            }
        }
        refresh()
    }

    private fun needOn(): Boolean {
        if (!Bot.enabled) {
            Toast.makeText(this, "আগে Home-এ ON করুন", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveCode() {
        val t = codeBox.text.toString()
        Store.write(this, "playbook.txt", t)
        if (t.isNotBlank()) {
            val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date())
            Store.append(this, "scripts.txt", "=== স্ক্রিপ্ট $ts ===\n$t\n\n")
        }
        lastMemLen = -1L
        Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun runCode(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "কোড বক্স খালি", Toast.LENGTH_SHORT).show()
            return
        }
        Store.write(this, "playbook.txt", text)
        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date())
        Store.append(this, "scripts.txt", "=== স্ক্রিপ্ট (চালানো) $ts ===\n$text\n\n")
        lastMemLen = -1L
        if (!needOn()) return
        if (Bot.busy) Bot.cancel = true
        Bot.submit("script", text)
        Bot.log("📨 স্ক্রিপ্ট পাঠানো হলো। Telegram-এ যান")
        showTab(0)
    }

    private fun stopJob() {
        Bot.cancel = true
        Bot.log("⏹ থামানোর অনুরোধ পাঠানো হলো")
    }

    private fun startLearn() {
        if (!needOn()) return
        if (Bot.busy) Bot.cancel = true
        Bot.submit("explore", "")
        Bot.log("📨 শেখা শুরু হবে। Telegram-এ CherryPlay Mini App খুলুন")
        Toast.makeText(this, "Telegram-এ Mini App খুলে হাত না দিয়ে থাকুন", Toast.LENGTH_LONG).show()
        showTab(0)
    }

    private fun memoryDoc(maxChars: Int): String {
        val mem = Store.read(this, "memory.txt")
        val scripts = Store.read(this, "scripts.txt")
        var all = "##### শেখা পেজ ও ট্যাপ #####\n" + mem +
            "\n##### সেভ করা স্ক্রিপ্ট #####\n" + scripts
        if (all.length > maxChars) all = all.substring(all.length - maxChars)
        return all
    }

    private fun fmtItem(it: Item): String =
        if (it.clickable) "[ট্যাপ ${it.rect.centerX()},${it.rect.centerY()}] ${it.label.take(60)}"
        else "[লেখা] ${it.label.take(60)}"

    private fun copyPackage() {
        val req = aiRequest.text.toString().trim()
        val screen = Bot.lastItems.take(120).joinToString("\n") { fmtItem(it) }
        val mem = Store.read(this, "memory.txt")
        val memTail = if (mem.length > 5000) mem.substring(mem.length - 5000) else mem
        val logTail = Bot.dump().lines().takeLast(40).joinToString("\n")
        val sb = StringBuilder()
        sb.append("আমি TG Assistant অ্যাপ ব্যবহার করছি (Telegram Mini App অটোমেশন)। নিচের তথ্য দেখে আমার জন্য স্ক্রিপ্ট লিখে দাও। শুধু স্ক্রিপ্ট কোড-ব্লকে দেবে।\n\n")
        sb.append("## আমার চাওয়া\n").append(if (req.isEmpty()) "(লেখা হয়নি)" else req).append("\n\n")
        sb.append("## স্ক্রিপ্টের কমান্ড\n")
        sb.append("WAIT সেকেন্ড | WAIT_TEXT \"লেখা\" সেকেন্ড | TAP_TEXT \"লেখা\" [first|last] | TAP_XY x y | SWIPE_UP | SWIPE_DOWN | BACK | LOG \"বার্তা\" | STOP\n")
        sb.append("REPEAT n ... END | IF_TEXT \"লেখা\" ... END | IF_NOT_TEXT \"লেখা\" ... END | # দিয়ে মন্তব্য\n\n")
        sb.append("## শেষ দেখা Telegram স্ক্রিন\n").append(screen).append("\n\n")
        sb.append("## শেখা মেমরি (শেষ অংশ)\n").append(memTail).append("\n\n")
        sb.append("## সাম্প্রতিক লগ\n").append(logTail).append("\n")
        copyText(sb.toString(), "প্যাকেজ")
    }

    private fun copyText(text: String, what: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "$what খালি", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(what, text))
        Toast.makeText(this, "$what কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    // ---------- রিফ্রেশ ----------

    private fun refresh() {
        val acc = accessibilityOn()
        status.text = when {
            !acc -> "Accessibility বন্ধ। বাটনে চাপুন, সেটিংসে চালু করুন"
            !Bot.enabled -> "প্রস্তুত। ON করে Telegram খুলুন"
            Bot.busy && Bot.inTelegram -> "▶ কাজ করছে (স্ক্রিপ্ট/শেখা)"
            Bot.inTelegram -> "▶ Telegram-এ আছে, নজর রাখছে"
            else -> "⏸ বিরতি: Telegram-এ গেলে নিজে চালু হবে"
        }
        powerText.text = if (Bot.enabled) "ON" else "OFF"
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.gradientType = GradientDrawable.SWEEP_GRADIENT
        val ink: Int
        if (!Bot.enabled) {
            g.colors = intArrayOf(Color.parseColor("#5a1a14"), Color.parseColor("#2a0e0b"), Color.parseColor("#5a1a14"))
            ink = Color.parseColor("#ffb09a")
        } else if (Bot.inTelegram) {
            g.colors = intArrayOf(Color.parseColor(RED), Color.parseColor(AMB), Color.parseColor(RED))
            ink = Color.parseColor("#2a0805")
        } else {
            g.colors = intArrayOf(Color.parseColor("#c77a00"), Color.parseColor("#ffd27a"), Color.parseColor("#c77a00"))
            ink = Color.parseColor("#2a0805")
        }
        core.background = g
        powerIcon.tint = ink
        powerIcon.invalidate()
        powerText.setTextColor(ink)

        autoBtn.text = if (autoOn()) "অটো-রান: চালু" else "অটো-রান: বন্ধ"

        val memLen = java.io.File(filesDir, "memory.txt").length() +
            java.io.File(filesDir, "scripts.txt").length()
        if (memLen != lastMemLen) {
            lastMemLen = memLen
            val doc = memoryDoc(30000)
            pageCount = Store.read(this, "memory.txt").split("=== ").size - 1
            if (tab == 2) {
                docView.text = if (doc.length < 60) "(এখনো কিছু শেখেনি। 🎓 শেখা চাপুন)" else doc
            }
        }
        chipPages.text = "$pageCount\nশেখা পেজ"
        chipJob.text = (if (Bot.busy) "চলছে" else "অলস") + "\nকাজ"
        chipTg.text = (if (Bot.inTelegram) "হ্যাঁ" else "না") + "\nTelegram-এ"

        val txt = Bot.dump()
        if (tab == 0 && txt != logView.text.toString()) {
            logView.text = txt
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        Store.write(this, "playbook.txt", codeBox.text.toString())
    }
}
