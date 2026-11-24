package com.opu.tgassist

object Script {
    fun parse(text: String): List<Cmd> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val stack = mutableListOf<MutableList<Cmd>>()
        stack.add(mutableListOf())
        for (raw in lines) {
            val tokens = raw.split(" ").filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            val name = tokens[0].uppercase()
            val args = tokens.drop(1)
            val current = stack.last()
            when (name) {
                "REPEAT" -> {
                    val n = args.firstOrNull()?.toIntOrNull() ?: 1
                    val body = mutableListOf<Cmd>()
                    current.add(Block("REPEAT", listOf(n.toString()), body))
                    stack.add(body)
                }
                "IF_TEXT", "IF_NOT_TEXT" -> {
                    val cond = args.joinToString(" ")
                    val body = mutableListOf<Cmd>()
                    current.add(Block(name, listOf(cond), body))
                    stack.add(body)
                }
                "END" -> {
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
                else -> current.add(Simple(name, args))
            }
        }
        return stack.first()
    }
}
