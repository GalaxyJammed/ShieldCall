package com.example.shieldcall

object Silenced {
    private val marks = HashMap<String, Long>()
    private const val WINDOW = 3 * 60 * 1000L

    @Synchronized
    fun mark(number: String) {
        marks[number] = System.currentTimeMillis()
    }

    @Synchronized
    fun isSilenced(number: String): Boolean {
        val t = marks[number] ?: return false
        return System.currentTimeMillis() - t < WINDOW
    }

    @Synchronized
    fun clear(number: String) {
        marks.remove(number)
    }
}