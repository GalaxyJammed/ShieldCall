package com.example.shieldcall

import java.text.Normalizer

object DialerSuggest {
    private const val MAP = "22233344455566677778889999"

    private fun words(name: String): List<String> {
        val plain = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).filter { it.code < 0x300 || it.code > 0x36F }
        return plain.split(' ', '-', '.', '_')
            .map { w -> w.filter { it in 'a'..'z' }.map { MAP[it - 'a'] }.joinToString("") }
            .filter { it.isNotEmpty() }
    }

    fun find(contacts: List<ContactItem>, typed: String, limit: Int = 3): List<ContactItem> {
        val digits = typed.filter { it.isDigit() }
        if (digits.length < 2) return emptyList()
        return contacts.asSequence()
            .filter { c ->
                c.key.contains(digits) ||
                        c.raw.filter { it.isDigit() }.contains(digits) ||
                        words(c.name).any { it.startsWith(digits) }
            }
            .distinctBy { it.key }
            .take(limit)
            .toList()
    }
}