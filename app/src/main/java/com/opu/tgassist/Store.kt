package com.opu.tgassist

import android.content.Context
import java.io.File

object Store {
    fun read(ctx: Context, name: String): String =
        try { File(ctx.filesDir, name).readText() } catch (e: Exception) { "" }

    fun write(ctx: Context, name: String, text: String) {
        try { File(ctx.filesDir, name).writeText(text) } catch (e: Exception) { }
    }

    fun append(ctx: Context, name: String, text: String) {
        try {
            val f = File(ctx.filesDir, name)
            if (f.length() > 400000) return
            f.appendText(text)
        } catch (e: Exception) { }
    }
}
