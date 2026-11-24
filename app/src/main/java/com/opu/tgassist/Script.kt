package com.opu.tgassist

sealed class Cmd
class Simple(val name: String, val args: List<String>) : Cmd()
class Block(val name: String, val args: List<String>, val body: MutableList<Cmd>) : Cmd()

object Script {
    private val tok = Regex("\"([^\"]*)\"|(\\S+)")

    fun parse(src: String): List<Cmd> {
        val root = ArrayList<Cmd>()
        val stack = ArrayList<MutableList<Cmd>>()
        stack.add(root)
        for (raw in src.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = tok.findAll(line).map { m -> m.groups[1]?.value ?: m.groups[2]!!.value }.toList()
            if (parts.isEmpty()) continue
            val name = parts[0].uppercase()
            val args = parts.drop(1)
            when (name) {
                "REPEAT", "IF_TEXT", "IF_NOT_TEXT" -> {
                    val b = Block(name, args, ArrayList())
                    stack[stack.size - 1].add(b)
                    stack.add(b.body)
                }
                "END" -> {
                    if (stack.size > 1) stack.removeAt(stack.size - 1)
                }
                else -> stack[stack.size - 1].add(Simple(name, args))
            }
        }
        return root
    }
}
