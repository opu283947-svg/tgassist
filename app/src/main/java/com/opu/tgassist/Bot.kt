package com.opu.tgassist

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class Job(val kind: String, val text: String)

object Bot {
    @Volatile var enabled = false
    @Volatile var serviceConnected = false
    @Volatile var inTelegram = false
    @Volatile var cancel = false
    @Volatile var busy = false
    @Volatile var lastOwnTapAt = 0L
    @Volatile var lastItems: List<Item> = emptyList()

    private var pending: Job? = null
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(msg: String) {
        lines.addLast(fmt.format(Date()) + "  " + msg)
        while (lines.size > 500) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()

    @Synchronized
    fun submit(kind: String, text: String) {
        pending = Job(kind, text)
    }

    @Synchronized
    fun takeJob(): Job? {
        val j = pending
        pending = null
        return j
    }
}
