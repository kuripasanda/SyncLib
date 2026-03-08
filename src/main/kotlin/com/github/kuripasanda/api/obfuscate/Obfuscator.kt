package com.github.kuripasanda.api.obfuscate

interface Obfuscator {

    fun obfuscate(input: String): String

    fun deobfuscate(input: String): String

}