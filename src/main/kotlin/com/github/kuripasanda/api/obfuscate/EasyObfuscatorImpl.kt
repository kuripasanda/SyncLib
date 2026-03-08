package com.github.kuripasanda.api.obfuscate

import java.util.Base64

/**
 * 文字列の難読化と復号を行うユーティリティクラス。
 * 簡単なXOR暗号とBase64エンコードを組み合わせて、文字列を難読化します。
 * 注意: この方法はセキュリティ目的には適していません。あくまで軽い難読化のためのものです。
 */
class EasyObfuscatorImpl(
    private val key: String
): Obfuscator {

    /**
     * 文字列を難読化（XOR + Base64）
     */
    override fun obfuscate(input: String): String {
        val xored = input.mapIndexed { index, char ->
            (char.code xor key[index % key.length].code).toChar()
        }.joinToString("")

        return Base64.getEncoder().encodeToString(xored.toByteArray())
    }

    /**
     * 難読化された文字列を復号
     */
    override fun deobfuscate(input: String): String {
        val decoded = String(Base64.getDecoder().decode(input))

        return decoded.mapIndexed { index, char ->
            (char.code xor key[index % key.length].code).toChar()
        }.joinToString("")
    }

}