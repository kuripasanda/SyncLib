package com.github.kuripasanda.api.network

import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

object SplitPacketUtil {

    /** ネットワークパケットの最大サイズ */
    const val MAX_BYTES_PER_PACKET = 1024 * 32 // 32KiB


    /**
     * 与えられた文字列のリストを、1パケットあたりのバイト数が[MAX_BYTES_PER_PACKET]を超えないように分割します。
     */
    fun splitStringList(originalList: List<String>): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        var currentChunk = mutableListOf<String>()
        var currentBytes = 0

        for (s in originalList) {
            // 文字列のバイト数 + VarIntのオーバーヘッド(約5バイト)を計算
            val stringBytes = s.toByteArray(StandardCharsets.UTF_8).size + 5

            if (currentBytes + stringBytes > MAX_BYTES_PER_PACKET && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentBytes = 0
            }
            currentChunk.add(s)
            currentBytes += stringBytes
        }

        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)
        return chunks
    }


    /**
     * 与えられたマップを、1パケットあたりのバイト数が[MAX_BYTES_PER_PACKET]を超えないように分割します。
     * キーと値の両方のバイト数を考慮し、さらにVarIntのオーバーヘッドも加味して分割します。
     * キーがResourceLocationの場合のバージョンです。
     * ResourceLocationは通常、"namespace:path"の形式で表されるため、文字列としてのバイト数を計算する際にはtoString()を使用してリソースロケーションを文字列に変換し、そのバイト数を考慮します。
     */
    fun splitResourceLocationMap(originalMap: Map<ResourceLocation, String>): List<List<Pair<ResourceLocation, String>>> {
        val chunks = mutableListOf<List<Pair<ResourceLocation, String>>>()
        var currentChunk = mutableListOf<Pair<ResourceLocation, String>>()
        var currentBytes = 0

        for ((key, value) in originalMap) {
            // キーと値の合計バイト数 + VarIntの余裕分(約10バイト)
            val entryBytes = key.toString().toByteArray(StandardCharsets.UTF_8).size + value.toByteArray(StandardCharsets.UTF_8).size + 10

            if (currentBytes + entryBytes > MAX_BYTES_PER_PACKET && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentBytes = 0
            }
            currentChunk.add(key to value)
            currentBytes += entryBytes
        }

        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)
        return chunks
    }

    /**
     * 与えられたマップを、1パケットあたりのバイト数が[MAX_BYTES_PER_PACKET]を超えないように分割します。
      * キーと値の両方のバイト数を考慮し、さらにVarIntのオーバーヘッドも加味して分割します。
     */
    fun splitStringMap(originalMap: Map<String, String>): List<List<Pair<String, String>>> {
        val chunks = mutableListOf<List<Pair<String, String>>>()
        var currentChunk = mutableListOf<Pair<String, String>>()
        var currentBytes = 0

        for ((key, value) in originalMap) {
            // キーと値の合計バイト数 + VarIntの余裕分(約10バイト)
            val entryBytes = key.toByteArray().size + value.toByteArray().size + 10

            if (currentBytes + entryBytes > MAX_BYTES_PER_PACKET && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentBytes = 0
            }
            currentChunk.add(key to value)
            currentBytes += entryBytes
        }

        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)
        return chunks
    }

}