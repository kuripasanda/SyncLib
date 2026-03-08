package com.github.kuripasanda.api.network.server

import com.github.kuripasanda.SyncLib
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * クライアントにレジストリのハッシュ値を送信するためのパケット
 * 通常、このパケットは複数のパートに分割されて送信されます。全てのパートが送信された後[SyncLibRegistryHashesCompleteS2CPacket]が送信されます。
 * @see SyncLibRegistryHashesCompleteS2CPacket
 */
class SyncLibRegistryHashesS2CPacket(
    val data: Data
): CustomPacketPayload {

    companion object {
        val PAYLOAD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "sync_registry_hashes")
        val ID = CustomPacketPayload.Type<SyncLibRegistryHashesS2CPacket>(PAYLOAD_ID)

        val CODEC: StreamCodec<ByteBuf, SyncLibRegistryHashesS2CPacket> = StreamCodec.composite(
            Data.STREAM_CODEC,
            SyncLibRegistryHashesS2CPacket::data,
            ::SyncLibRegistryHashesS2CPacket
        )

        init {
            PayloadTypeRegistry.configurationS2C().register(ID, CODEC)
        }
    }

    data class Data(
        val registryId: ResourceLocation,
        val hashes: Map<String, String>
    ) {
        companion object {
            private val REGISTRY_HASHES_CODEC: StreamCodec<ByteBuf, Map<String, String>> =
                ByteBufCodecs.map(
                    { LinkedHashMap<String, String>() },
                    ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.STRING_UTF8
                )

            val STREAM_CODEC: StreamCodec<ByteBuf, Data> = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                Data::registryId,
                REGISTRY_HASHES_CODEC,
                Data::hashes,
                ::Data
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}