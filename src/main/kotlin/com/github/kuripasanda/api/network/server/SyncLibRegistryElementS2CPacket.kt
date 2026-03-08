package com.github.kuripasanda.api.network.server

import com.github.kuripasanda.SyncLib
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * サーバーからクライアントへ、レジストリの要素を同期するためのパケット
 * registryElement はパケット上で JSON 文字列として送るため、KSerializer を使用してシリアライズ/デシリアライズを行います。
 */
class SyncLibRegistryElementS2CPacket(
    val registryId: ResourceLocation,
    val elementKey: String,
    val registryElement: ByteArray
): CustomPacketPayload {

    companion object {

        val PAYLOAD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "sync_registry_element")
        val ID = CustomPacketPayload.Type<SyncLibRegistryElementS2CPacket>(PAYLOAD_ID)

        val CODEC: StreamCodec<ByteBuf, SyncLibRegistryElementS2CPacket> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            SyncLibRegistryElementS2CPacket::registryId,
            ByteBufCodecs.STRING_UTF8,
            SyncLibRegistryElementS2CPacket::elementKey,
            ByteBufCodecs.BYTE_ARRAY,
            SyncLibRegistryElementS2CPacket::registryElement,
            ::SyncLibRegistryElementS2CPacket
        )

        init {
            // パケットの登録
            PayloadTypeRegistry.configurationS2C().register(ID, CODEC)
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
        }

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

}