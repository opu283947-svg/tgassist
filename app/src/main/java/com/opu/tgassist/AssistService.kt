package com.opu.tgassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistService : AccessibilityService() {

    private var lastDumpAt = 0L
    private var lastSig = ""
    private var lastTapAt = 0L

    private fun isTelegram(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("telegram") || p.contains("challegram") || p.contains("nekogram")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bot.serviceConnected = true
        Bot.log("Accessibility চালু (v0.4: Telegram-only, বাইরে গেলে বিরতি)")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Bot.serviceConnected = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Telegram-এর ভেতরে আছি কি না ট্র্যাক করা (বাইরে গেলে বিরতি, বন্ধ নয়)
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
        if (!isTelegram(pkg)) return   // Telegram ছাড়া অন্য অ্যাপের কিছুই পড়া হয় না

        val now = System.currentTimeMillis()
        if (now - lastDumpAt < 1500) return
        lastDumpAt = now

        val root = rootInActiveWindow ?: run {
            Bot.log("[$pkg] স্ক্রিন পড়া যাচ্ছে না (root null)")
            return
        }
        if (!isTelegram(root.packageName?.toString() ?: "")) return

        val texts = ArrayList<String>()
        collect(root, texts, 0)
        val sig = pkg + texts.joinToString("|")
        if (sig != lastSig) {
            lastSig = sig
            Bot.log("── [$pkg] ${texts.size}টা আইটেম ──")
            if (texts.isEmpty()) Bot.log("(কোনো লেখা পাওয়া যায়নি, সম্ভবত ছবি/WebView)")
            for (t in texts.take(70)) Bot.log(t)
        }

        // চ্যাটে "Open CherryPlay" থাকলে ওটার ওপর আঙুলের ট্যাপ
        if (now - lastTapAt > 8000) {
            val found = ArrayList<AccessibilityNodeInfo>()
            findAll(root, "Open CherryPlay", found, 0)
            if (found.isNotEmpty()) {
                lastTapAt = now
                tapNode(found.last(), "Open CherryPlay")
            }
        }
    }

    private fun findAll(n: AccessibilityNodeInfo?, label: String,
                        out: ArrayList<AccessibilityNodeInfo>, depth: Int) {
        if (n == null || depth > 40) return
        val t = n.text?.toString().orEmpty()
        val d = n.contentDescription?.toString().orEmpty()
        if (t.contains(label, true) || d.contains(label, true)) out.add(n)
        for (i in 0 until n.childCount) findAll(n.getChild(i), label, out, depth + 1)
    }

    private fun tapNode(n: AccessibilityNodeInfo, label: String) {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) {
            Bot.log("⚠ \"$label\" পাওয়া গেছে কিন্তু জায়গা (bounds) ঠিক নেই: $r")
            return
        }
        val ok = tapAt(r.exactCenterX(), r.exactCenterY(), label)
        if (!ok) {
            // বিকল্প: ACTION_CLICK
            var p: AccessibilityNodeInfo? = n
            var depth = 0
            while (p != null && depth < 6) {
                if (p.isClickable) {
                    val c = p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Bot.log("↩ বিকল্প ACTION_CLICK: ${if (c) "সফল" else "ব্যর্থ"}")
                    return
                }
                p = p.parent
                depth++
            }
        }
    }

    private fun tapAt(x: Float, y: Float, label: String): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val ok = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                Bot.log("✅ ট্যাপ সম্পন্ন")
            }
            override fun onCancelled(g: GestureDescription?) {
                Bot.log("⚠ ট্যাপ বাতিল (হাত লেগেছে?), আবার চেষ্টা করব")
                lastTapAt = System.currentTimeMillis() - 6500
            }
        }, null)
        Bot.log("👆 ট্যাপ \"$label\" (${x.toInt()},${y.toInt()}) পাঠানো=$ok")
        return ok
    }

    private fun collect(n: AccessibilityNodeInfo?, out: ArrayList<String>, depth: Int) {
        if (n == null || depth > 40 || out.size > 250) return
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        val label = if (t.isNotEmpty()) t else d
        if (label.isNotEmpty()) {
            if (n.isClickable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                out.add("[ট্যাপ ${r.centerX()},${r.centerY()}] ${label.take(70)}")
            } else {
                out.add("[লেখা] ${label.take(70)}")
            }
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }
}
