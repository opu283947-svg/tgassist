package com.opu.tgassist

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistService : AccessibilityService() {

    private var lastDumpAt = 0L
    private var lastSig = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bot.serviceConnected = true
        Bot.log("Accessibility চালু হয়েছে")
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

        val now = System.currentTimeMillis()
        if (now - lastDumpAt < 2000) return
        lastDumpAt = now

        val root = rootInActiveWindow ?: run {
            Bot.log("[$pkg] স্ক্রিন পড়া যাচ্ছে না (root null)")
            return
        }
        val texts = ArrayList<String>()
        collect(root, texts, 0)
        val sig = pkg + texts.joinToString("|")
        if (sig == lastSig) return
        lastSig = sig

        Bot.log("── [$pkg] ${texts.size}টা আইটেম ──")
        if (texts.isEmpty()) {
            Bot.log("(কোনো লেখা পাওয়া যায়নি, সম্ভবত ছবি/WebView)")
        }
        for (t in texts.take(60)) Bot.log(t)
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
