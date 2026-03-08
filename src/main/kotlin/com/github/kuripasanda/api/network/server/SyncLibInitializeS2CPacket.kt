package com.github.kuripasanda.api.network.server

import com.github.kuripasanda.SyncLib
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class SyncLibInitializeS2CPacket(
    val data: Data
): CustomPacketPayload {

    companion object {

        val PAYLOAD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "sync_initialize")
        val ID = CustomPacketPayload.Type<SyncLibInitializeS2CPacket>(PAYLOAD_ID)

        val CODEC: StreamCodec<ByteBuf, SyncLibInitializeS2CPacket> = StreamCodec.composite(
            Data.STREAM_CODEC,
            SyncLibInitializeS2CPacket::data,
            ::SyncLibInitializeS2CPacket
        )

        init {
            // パケットの登録
            PayloadTypeRegistry.configurationS2C().register(ID, CODEC)
        }
    }


    /**
     * サーバーからクライアントへの初期化パケットのデータクラス
     * @param serverId サーバーの識別子。クライアントはこれを使用してサーバーを識別し、同期プロセスを管理します。
     * @param obfuscateKey データの難読化に使用されるキー
     */
    data class Data(
        val serverId: String,
        val obfuscateKey: String
    ) {
        companion object {

            val STREAM_CODEC: StreamCodec<ByteBuf, Data> = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                Data::serverId,
                ByteBufCodecs.STRING_UTF8,
                Data::obfuscateKey,
                ::Data
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

}