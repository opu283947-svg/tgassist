package com.opu.tgassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Item(val label: String, val clickable: Boolean, val rect: Rect)

class AssistService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    private var lastDumpAt = 0L
    private var lastSig = ""
    private var lastTapAt = 0L
    private var prevItems: List<Item> = emptyList()
    private var lastPrevAt = 0L
    private var watchStep = 0
    private val seenPages = HashSet<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun isTelegram(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("telegram") || p.contains("challegram") || p.contains("nekogram")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bot.serviceConnected = true
        Bot.log("Accessibility চালু (v11-fixed)")
        startWorker()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Bot.serviceConnected = false
        Bot.cancel = true
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    // ---------------- ইভেন্ট ----------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        val noise = pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")
        if (!noise) {
            val nowIn = isTelegram(pkg)
            val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            if (nowIn && !Bot.inTelegram) {
                Bot.inTelegram = true
                if (Bot.enabled) Bot.log("▶ Telegram-এ আছি, কাজ চলছে")
            } else if (!nowIn && isStateChange && Bot.inTelegram) {
                Bot.inTelegram = false
                if (Bot.enabled) Bot.log("⏸ Telegram-এর বাইরে ($pkg), বিরতি। ফিরলে নিজে চলবে")
            }
        }

        if (!Bot.enabled) return
        if (!isTelegram(pkg)) return

        // Watch mode: capture screen before user tap (for record)
        if (Bot.watch && !Bot.busy && event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val nowW = System.currentTimeMillis()
            if (nowW - lastPrevAt > 700) {
                lastPrevAt = nowW
                val snap = currentItems()
                if (snap != null) prevItems = snap
            }
        }

        // User tap detection (for record mode)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            noteUserTap(event)
            return
        }

        if (Bot.busy) return
        val now = System.currentTimeMillis()
        if (now - lastDumpAt < 1500) return
        lastDumpAt = now

        val items = currentItems() ?: return
        val sig = sigOf(items)
        if (sig != lastSig) {
            lastSig = sig
            Bot.log("── পেজ বদলেছে (${items.size}টা আইটেম) ──")
            for (item in items.take(40)) Bot.log(fmt(item))
            recordPage("নিজে নিজে", items)
        }

        // AUTO-TAP: Only when NOT watching, NOT busy, and in Mini App
        if (!Bot.watch && !Bot.busy && inMiniApp()) {
            // 1. Dismiss "Maybe later" popup
            val dm = items.filter { it.label.equals("Maybe later", true) }
            if (dm.isNotEmpty() && now - lastTapAt > 3000) {
                lastTapAt = now
                val r2 = dm[dm.size - 1].rect
                Bot.log("🧹 পপআপ বন্ধ করছি: Maybe later")
                tapAtAsync(r2.exactCenterX(), r2.exactCenterY())
                return
            }
            // 2. Auto-tap "Open CherryPlay" ONLY if we're in bot chat (not Mini App yet)
            if (now - lastTapAt > 8000) {
                val m = items.filter { it.label.contains("Open CherryPlay", true) }
                if (m.isNotEmpty() && !inMiniApp()) {  // <-- KEY FIX: only tap if NOT in Mini App
                    lastTapAt = now
                    val r = m[m.size - 1].rect
                    tapAtAsync(r.exactCenterX(), r.exactCenterY())
                }
            }
        }
    }

    private fun watchRecord(event: AccessibilityEvent) {
        if (System.currentTimeMillis() - Bot.lastOwnTapAt < 2500) return
        val src = event.source
        var label = src?.text?.toString()?.trim().orEmpty()
        if (label.isEmpty()) label = src?.contentDescription?.toString()?.trim().orEmpty()
        if (label.isEmpty()) label = event.text.joinToString(" ").trim()
        val r = Rect()
        src?.getBoundsInScreen(r)
        val before = prevItems
        watchStep++
        val step = watchStep
        Bot.log("🔴 ধাপ $step: আপনি ট্যাপ করলেন \"${label.take(40)}\"")
        val goodLabel = label.isNotEmpty() && label.length <= 30 &&
            label.none { ch -> ch.isDigit() || ch == '"' }
        var anchor = ""
        if (goodLabel) {
            anchor = label
        } else {
            for (x in before) {
                if (x.label.length in 3..25 && x.label.none { ch -> ch.isDigit() || ch == '"' }) {
                    anchor = x.label
                    break
                }
            }
        }
        val gen = StringBuilder()
        if (anchor.isNotEmpty()) gen.append("WAIT_TEXT \"").append(anchor).append("\" 10\n")
        if (goodLabel) {
            val same = before.filter { x -> x.label.equals(label, true) }
            var which = "last"
            if (same.size > 1) {
                val top = same.minOf { x -> x.rect.centerY() }
                val bot = same.maxOf { x -> x.rect.centerY() }
                if (Math.abs(r.centerY() - top) < Math.abs(r.centerY() - bot)) which = "first"
            }
            gen.append("TAP_EXACT \"").append(label).append("\" ").append(which).append("\n")
        } else {
            gen.append("TAP_XY ").append(r.centerX()).append(" ").append(r.centerY()).append("\n")
        }
        gen.append("WAIT 3\n")
        Bot.addStep(gen.toString())
        mainHandler.postDelayed({
            val after = currentItems() ?: emptyList()
            val bset = before.map { x -> x.label }.toSet()
            val aset = after.map { x -> x.label }.toSet()
            val added = after.map { x -> x.label }.filter { l -> l !in bset }.distinct().take(14)
            val gone = before.map { x -> x.label }.filter { l -> l !in aset }.distinct().take(8)
            val was = before.map { x -> x.label }.filter { l -> l.none { ch -> ch.isDigit() } }
                .distinct().take(10)
            val sb = StringBuilder()
            sb.append("STEP ").append(step).append("  USER_TAP \"").append(label).append("\" ")
                .append(r.centerX()).append(",").append(r.centerY()).append("\n")
            sb.append("  আগে ছিল: ").append(was.joinToString(" | ")).append("\n")
            sb.append("  নতুন এসেছে: ").append(added.joinToString(" | ")).append("\n")
            sb.append("  চলে গেছে: ").append(gone.joinToString(" | ")).append("\n")
            Store.append(this, "memory.txt", sb.toString())
            Bot.log("   → নতুন এসেছে: " + added.take(6).joinToString(" | "))
        }, 1500)
    }

    private fun noteUserTap(event: AccessibilityEvent) {
        if (Bot.watch) {
            watchRecord(event)
            return
        }
        if (Bot.busy) return
        if (System.currentTimeMillis() - Bot.lastOwnTapAt < 2500) return
        val src = event.source
        var label = src?.text?.toString()?.trim().orEmpty()
        if (label.isEmpty()) label = src?.contentDescription?.toString()?.trim().orEmpty()
        if (label.isEmpty()) label = event.text.joinToString(" ").trim()
        val r = Rect()
        src?.getBoundsInScreen(r)
        Bot.log("👤 আপনার ট্যাপ: \"${label.take(40)}\" (${r.centerX()},${r.centerY()})")
        Store.append(this, "memory.txt", "USER_TAP \"$label\" ${r.centerX()},${r.centerY()}\n")
    }

    // ---------------- স্ক্রিন পড়া ----------------

    private fun collectItems(n: AccessibilityNodeInfo?, out: ArrayList<Item>, depth: Int) {
        if (n == null || depth > 40 || out.size > 400) return
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        val label = if (t.isNotEmpty()) t else d
        if (label.isNotEmpty()) {
            val r = Rect()
            n.getBoundsInScreen(r)
            out.add(Item(label, n.isClickable, r))
        }
        for (i in 0 until n.childCount) collectItems(n.getChild(i), out, depth + 1)
    }

    private fun currentItems(): List<Item>? {
        val root = rootInActiveWindow ?: return null
        if (!isTelegram(root.packageName?.toString() ?: "")) return null
        val out = ArrayList<Item>()
        collectItems(root, out, 0)
        Bot.lastItems = out
        return out
    }

    private fun sigOf(items: List<Item>): String =
        items.map { it.label }.filter { s -> s.none { ch -> ch.isDigit() } }.toSortedSet().joinToString("|")

    private fun fmt(item: Item): String =
        if (item.clickable) "[ট্যাপ ${item.rect.centerX()},${item.rect.centerY()}] ${item.label.take(60)}"
        else "[লেখা] ${item.label.take(60)}"

    @Synchronized
    private fun recordPage(label: String, items: List<Item>): Boolean {
        val sig = sigOf(items)
        if (sig.isEmpty() || !seenPages.add(sig)) return false
        val sb = StringBuilder()
        sb.append("=== ").append(timeFmt.format(Date())).append(" | ").append(label).append(" ===\n")
        for (item in items.take(150)) {
            if (item.clickable) {
                sb.append("[T ").append(item.rect.centerX()).append(",").append(item.rect.centerY())
                    .append("] ").append(item.label.take(70)).append("\n")
            } else {
                sb.append("[L] ").append(item.label.take(70)).append("\n")
            }
        }
        Store.append(this, "memory.txt", sb.toString())
        Bot.log("📘 শিখলাম: $label (${items.size}টা আইটেম)")
        return true
    }

    private fun matchItem(x: Item, label: String, exact: Boolean): Boolean =
        if (exact) x.label.equals(label, true) else x.label.contains(label, true)

    private fun hasMatch(label: String, exact: Boolean): Boolean {
        if (label.isEmpty()) return false
        return currentItems()?.any { matchItem(it, label, exact) } == true
    }

    // FIXED: Better Mini App detection - wait for bottom nav
    private fun inMiniApp(): Boolean {
        val items = currentItems() ?: return false
        val keys = listOf("Home", "Tasks", "Games", "Friends", "Profile")
        val n = keys.count { k -> items.any { x -> x.label.equals(k, true) } }
        return n >= 3
    }

    private fun hasText(label: String): Boolean {
        if (label.isEmpty()) return false
        return currentItems()?.any { it.label.contains(label, true) } == true
    }

    // ---------------- ট্যাপ ও স্ক্রল ----------------

    private fun tapPath(x: Float, y: Float): Path {
        val p = Path()
        p.moveTo(x, y)
        return p
    }

    private fun tapAtAsync(x: Float, y: Float) {
        Bot.lastOwnTapAt = System.currentTimeMillis()
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath(x, y), 0, 90)).build()
        val ok = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                Bot.log("✅ ট্যাপ সম্পন্ন")
            }
            override fun onCancelled(d: GestureDescription?) {
                Bot.log("⚠ ট্যাপ বাতিল, আবার চেষ্টা করব")
                lastTapAt = System.currentTimeMillis() - 6500
            }
        }, null)
        Bot.log("👆 ট্যাপ (${x.toInt()},${y.toInt()}) পাঠানো=$ok")
    }

    private fun runGesture(path: Path, durMs: Long): Boolean {
        for (attempt in 1..3) {
            if (Bot.cancel) return false
            Bot.lastOwnTapAt = System.currentTimeMillis()
            val done = CountDownLatch(1)
            val okFlag = AtomicBoolean(false)
            mainHandler.post {
                val g = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durMs)).build()
                val sent = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) {
                        okFlag.set(true)
                        done.countDown()
                    }
                    override fun onCancelled(d: GestureDescription?) {
                        done.countDown()
                    }
                }, null)
                if (!sent) done.countDown()
            }
            done.await(5, TimeUnit.SECONDS)
            if (okFlag.get()) {
                Bot.lastOwnTapAt = System.currentTimeMillis()
                return true
            }
            Bot.log("⚠ অঙ্গভঙ্গি বাতিল ($attempt/3), আবার চেষ্টা")
            sleepC(1000)
        }
        return false
    }

    private fun tapXY(x: Float, y: Float): Boolean = runGesture(tapPath(x, y), 90)

    private fun swipe(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val x = w * 0.5f
        val y1 = if (up) h * 0.70f else h * 0.35f
        val y2 = if (up) h * 0.35f else h * 0.70f
        val p = Path()
        p.moveTo(x, y1)
        p.lineTo(x, y2)
        return runGesture(p, 500)
    }

    // ---------------- অপেক্ষা ----------------

    private fun sleepC(ms: Long) {
        var left = ms
        while (left > 0 && !Bot.cancel) {
            val s = if (left < 200L) left else 200L
            Thread.sleep(s)
            left -= s
        }
    }

    private fun gate(): Boolean {
        var logged = false
        while (!Bot.cancel) {
            if (Bot.enabled && Bot.inTelegram) return true
            if (!logged) {
                Bot.log("⏸ অপেক্ষা: Telegram-এ গেলে চলবে")
                logged = true
            }
            Thread.sleep(500)
        }
        return false
    }

    private fun waitStable(maxMs: Long) {
        val end = System.currentTimeMillis() + maxMs
        var prev = ""
        var same = 0
        while (System.currentTimeMillis() < end && !Bot.cancel) {
            val items = currentItems()
            val s = if (items != null) sigOf(items) else ""
            if (s.isNotEmpty() && s == prev) {
                same++
                if (same >= 2) return
            } else {
                same = 0
            }
            prev = s
            sleepC(400)
        }
    }

    private fun waitText(label: String, sec: Int): Boolean {
        val end = System.currentTimeMillis() + sec * 1000L
        while (System.currentTimeMillis() < end && !Bot.cancel) {
            if (!gate()) return false
            if (hasText(label)) return true
            sleepC(500)
        }
        return false
    }

    private fun tapText(label: String, last: Boolean, exact: Boolean = false): Boolean {
        val end = System.currentTimeMillis() + 10000L
        while (System.currentTimeMillis() < end && !Bot.cancel) {
            if (!gate()) return false
            if (hasMatch(label, exact)) {
                waitStable(3000)
                val items = currentItems()
                if (items == null) {
                    sleepC(500)
                    continue
                }
                val pool = items.filter { matchItem(it, label, exact) && it.rect.width() > 0 }
                if (pool.isEmpty()) {
                    sleepC(500)
                    continue
                }
                var pick = pool[0]
                for (c in pool) {
                    if (last) {
                        if (c.rect.centerY() >= pick.rect.centerY()) pick = c
                    } else {
                        if (c.rect.centerY() < pick.rect.centerY()) pick = c
                    }
                }
                Bot.log("👆 \"$label\" -> (${pick.rect.centerX()},${pick.rect.centerY()})")
                val ok = tapXY(pick.rect.exactCenterX(), pick.rect.exactCenterY())
                sleepC(1200)
                return ok
            }
            sleepC(500)
        }
        Bot.log("⚠ \"$label\" পাওয়া গেল না")
        return false
    }

    // ---------------- কাজ চালানো ----------------

    private fun startWorker() {
        if (worker?.isAlive == true) return
        val t = Thread {
            while (Bot.serviceConnected) {
                val job = Bot.takeJob()
                if (job == null) {
                    try {
                        Thread.sleep(300)
                    } catch (e: InterruptedException) {
                        return@Thread
                    }
                    continue
                }
                try {
                    runJob(job)
                } catch (e: Exception) {
                    Bot.log("⚠ ত্রুটি: ${e.message}")
                }
            }
        }
        t.isDaemon = true
        t.start()
        worker = t
    }

    private fun runJob(job: Job) {
        Bot.cancel = false
        Bot.busy = true
        try {
            when (job.kind) {
                "script" -> {
                    val cmds = Script.parse(job.text)
                    Bot.log("▶ প্লেবুক শুরু (${cmds.size}টা কমান্ড)")
                    val ok = runCmds(cmds)
                    Bot.log(if (ok) "✔ প্লেবুক শেষ" else "■ প্লেবুক থামল")
                }
                "explore" -> explore()
            }
        } finally {
            Bot.busy = false
        }
    }

    private fun runCmds(cmds: List<Cmd>): Boolean {
        for (c in cmds) {
            if (Bot.cancel) return false
            if (!gate()) return false
            if (c is Block) {
                val arg = c.args.firstOrNull() ?: ""
                if (c.name == "REPEAT") {
                    val n = (arg.toIntOrNull() ?: 1).coerceIn(1, 1000)
                    for (i in 1..n) {
                        if (!runCmds(c.body)) return false
                    }
                } else if (c.name == "IF_TEXT") {
                    if (hasText(arg)) {
                        if (!runCmds(c.body)) return false
                    }
                } else if (c.name == "IF_NOT_TEXT") {
                    if (!hasText(arg)) {
                        if (!runCmds(c.body)) return false
                    }
                }
            } else if (c is Simple) {
                if (!runSimple(c)) return false
            }
        }
        return true
    }

    private fun runSimple(c: Simple): Boolean {
        val a = c.args
        when (c.name) {
            "WAIT" -> {
                val s = a.firstOrNull()?.toDoubleOrNull() ?: 1.0
                sleepC((s * 1000).toLong())
            }
            "WAIT_TEXT" -> {
                val t = a.getOrNull(0) ?: ""
                val sec = a.getOrNull(1)?.toIntOrNull() ?: 10
                if (!waitText(t, sec)) {
                    Bot.log("⚠ WAIT_TEXT: \"$t\" আসেনি, থামলাম")
                    return false
                }
            }
            "TAP_TEXT" -> {
                val t = a.getOrNull(0) ?: ""
                val last = (a.getOrNull(1) ?: "last").lowercase() != "first"
                if (!tapText(t, last)) return false
            }
            "TAP_XY" -> {
                val x = a.getOrNull(0)?.toFloatOrNull()
                val y = a.getOrNull(1)?.toFloatOrNull()
                if (x == null || y == null) {
                    Bot.log("⚠ TAP_XY-তে x y সংখ্যা দিন")
                    return false
                }
                if (!tapXY(x, y)) return false
                sleepC(1200)
            }
            "EXPECT" -> {
                for (t in a) {
                    if (!hasText(t)) {
                        Bot.log("⚠ EXPECT: \"$t\" নেই, ভুল পেজে আছি, থামলাম")
                        return false
                    }
                }
            }
            "TAP_EXACT" -> {
                val t = a.getOrNull(0) ?: ""
                val last = (a.getOrNull(1) ?: "last").lowercase() != "first"
                if (!tapText(t, last, true)) return false
            }
            "SWIPE_UP" -> {
                swipe(true)
                sleepC(1200)
            }
            "SWIPE_DOWN" -> {
                swipe(false)
                sleepC(1200)
            }
            "BACK" -> {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                sleepC(1200)
            }
            "LOG" -> Bot.log("📝 " + a.joinToString(" "))
            "STOP" -> return false
            else -> Bot.log("? অজানা কমান্ড: ${c.name}")
        }
        return true
    }

    // ---------------- শেখা (৩ মিনিট) - FIXED ----------------

    private fun explore() {
        Bot.log("🎓 শেখা শুরু (সর্বোচ্চ ৩ মিনিট)। স্ক্রিনে হাত দেবেন না")
        var ready = false
        // Wait for Mini App to fully load with bottom nav
        for (w in 1..20) {
            if (Bot.cancel) return
            if (!gate()) return
            if (inMiniApp()) {
                // Extra wait for WebView to stabilize
                sleepC(2000)
                ready = true
                break
            }
            sleepC(1000)
        }
        if (!ready) {
            Bot.log("⚠ CherryPlay Mini App খোলা নেই (নিচে Home/Tasks/Games/Friends/Profile দেখা যাচ্ছে না)। কিছু ট্যাপ করিনি, থামলাম")
            return
        }
        val end = System.currentTimeMillis() + 180000L
        val first = currentItems()
        if (first != null) recordPage("শুরু", first)

        val plan = listOf<Pair<String, List<String>>>(
            Pair("Home", listOf<String>()),
            Pair("Tasks", listOf("Social", "Daily", "Exclusive", "Partner")),
            Pair("Games", listOf<String>()),
            Pair("Friends", listOf<String>()),
            Pair("Profile", listOf<String>())
        )
        for (entry in plan) {
            if (Bot.cancel || System.currentTimeMillis() > end) break
            if (!gate()) break
            if (!inMiniApp()) {
                Bot.log("⚠ Mini App থেকে বেরিয়ে গেছি, থামলাম")
                break
            }
            val tab = entry.first
            if (!tapText(tab, true, true)) {
                Bot.log("(\"$tab\" ট্যাব পাওয়া যায়নি)")
                continue
            }
            sleepC(2500)
            waitStable(3000)
            val its = currentItems()
            if (its != null) recordPage(tab, its)
            for (i in 1..3) {
                if (Bot.cancel) break
                swipe(true)
                sleepC(1500)
                val s = currentItems()
                if (s != null) recordPage("$tab স্ক্রল $i", s)
            }
            for (i in 1..3) {
                if (Bot.cancel) break
                swipe(false)
                sleepC(600)
            }
            for (sub in entry.second) {
                if (Bot.cancel || System.currentTimeMillis() > end) break
                if (inMiniApp() && tapText(sub, false)) {
                    sleepC(1800)
                    waitStable(3000)
                    val s2 = currentItems()
                    if (s2 != null) recordPage("$tab > $sub", s2)
                }
            }
        }
        Bot.log("🎓 শেখা শেষ। অ্যাপে \"মেমরি কপি\" চেপে এখানে পাঠান")
    }
}
