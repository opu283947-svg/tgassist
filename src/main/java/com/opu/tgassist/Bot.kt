package com.opu.tgassist

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class Job(val kind: String, val text: String)
class Cmd
class Simple(val name: String, val args: List<String>) : Cmd()
class Block(val name: String, val args: List<String>, val body: List<Cmd>) : Cmd()

object Bot {
    @Volatile var enabled = false
    @Volatile var inTelegram = false
    @Volatile var busy = false
    @Volatile var cancel = false
    @Volatile var watch = false
    @Volatile var serviceConnected = false
    @Volatile var lastOwnTapAt = 0L
    @Volatile var lastItems: List<Item> = emptyList()

    private val queue = ConcurrentLinkedQueue<Job>()
    private val steps = StringBuilder()
    private val logLines = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun submit(kind: String, text: String) = queue.offer(Job(kind, text))
    fun takeJob(): Job? = queue.poll()

    fun log(msg: String) {
        val line = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
        logLines.add(line)
        if (logLines.size > 500) logLines.removeAt(0)
        mainHandler.post { }
    }

    fun dump(): String = logLines.joinToString("\n")
    fun clear() { logLines.clear() }

    fun addStep(step: String) = steps.append(step)
    fun takeSteps(): String { val s = steps.toString(); steps.setLength(0); return s }
    fun clearSteps() { steps.setLength(0) }
}
