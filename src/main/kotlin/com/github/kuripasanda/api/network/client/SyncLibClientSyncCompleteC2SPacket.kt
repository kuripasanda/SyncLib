package com.github.kuripasanda.api.network.client

import com.github.kuripasanda.SyncLib
import com.github.kuripasanda.api.network.server.SyncLibInitializeS2CPacket
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * クライアントからサーバーへの、同期完了を通知するパケット。
 * @see SyncLibInitializeS2CPacket
 */
class SyncLibClientSyncCompleteC2SPacket(
    val serverId: String,
): CustomPacketPayload {

    companion object {

        val PAYLOAD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "sync_complete")
        val ID = CustomPacketPayload.Type<SyncLibClientSyncCompleteC2SPacket>(PAYLOAD_ID)

        val CODEC: StreamCodec<ByteBuf, SyncLibClientSyncCompleteC2SPacket> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SyncLibClientSyncCompleteC2SPacket::serverId,
            ::SyncLibClientSyncCompleteC2SPacket
        )

        init {
            // クライアント -> サーバー向けパケットとして登録
            PayloadTypeRegistry.configurationC2S().register(ID, CODEC)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

}