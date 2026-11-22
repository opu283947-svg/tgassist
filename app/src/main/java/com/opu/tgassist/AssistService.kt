package com.opu.tgassist

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistService : AccessibilityService() {

    private var lastDumpAt = 0L
    private var lastSig = ""
    private var lastTapAt = 0L
    private var lastOtherPkg = ""

    private fun isTelegram(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("telegram") || p.contains("challegram") || p.contains("nekogram")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bot.serviceConnected = true
        Bot.log("Accessibility চালু হয়েছে (v0.2: শুধু Telegram)")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Bot.serviceConnected = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Bot.enabled || event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Telegram ছাড়া অন্য অ্যাপের কিছুই পড়া বা লগ করা হবে না
        if (!isTelegram(pkg)) {
            if (pkg != lastOtherPkg) {
                lastOtherPkg = pkg
                Bot.log("(অন্য অ্যাপ: $pkg, কিছু পড়া হয়নি)")
            }
            return
        }
        lastOtherPkg = ""

        val now = System.currentTimeMillis()
        if (now - lastDumpAt < 2000) return
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
            for (t in texts.take(60)) Bot.log(t)
        }

        // প্রথম ট্যাপ পরীক্ষা: চ্যাটে "Open CherryPlay" থাকলে ট্যাপ
        if (now - lastTapAt > 6000) {
            if (tapByText(root, "Open CherryPlay")) lastTapAt = now
        }
    }

    private fun tapByText(root: AccessibilityNodeInfo, label: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(label)
        if (nodes.isNullOrEmpty()) return false
        // সবচেয়ে নিচের (নতুন) বাটনটা নেব
        val target = nodes.last()
        var n: AccessibilityNodeInfo? = target
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable) {
                val ok = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Bot.log("👆 ট্যাপ করা হলো: \"$label\" ${if (ok) "(সফল)" else "(ব্যর্থ)"}")
                return ok
            }
            n = n.parent
            depth++
        }
        Bot.log("⚠ \"$label\" পাওয়া গেছে কিন্তু ট্যাপযোগ্য নয়")
        return false
    }

    private fun collect(n: AccessibilityNodeInfo?, out: ArrayList<String>, depth: Int) {
        if (n == null || depth > 40 || out.size > 200) return
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        val label = if (t.isNotEmpty()) t else d
        if (label.isNotEmpty()) {
            val tag = if (n.isClickable) "[ট্যাপ]" else "[লেখা]"
            out.add("$tag ${label.take(70)}")
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }
}
