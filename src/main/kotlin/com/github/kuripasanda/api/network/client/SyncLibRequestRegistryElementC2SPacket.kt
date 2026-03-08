package com.github.kuripasanda.api.network.client

import com.github.kuripasanda.SyncLib
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class SyncLibRequestRegistryElementC2SPacket(
    val data: Data
): CustomPacketPayload {

    companion object {
        val PAYLOAD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "sync_request_registry_element")
        val ID = CustomPacketPayload.Type<SyncLibRequestRegistryElementC2SPacket>(PAYLOAD_ID)

        val CODEC: StreamCodec<ByteBuf, SyncLibRequestRegistryElementC2SPacket> = StreamCodec.composite(
            Data.STREAM_CODEC,
            SyncLibRequestRegistryElementC2SPacket::data,
            ::SyncLibRequestRegistryElementC2SPacket
        )

        init {
            PayloadTypeRegistry.configurationC2S().register(ID, CODEC)
        }

        fun build(registryId: ResourceLocation, elementIds: List<String>): SyncLibRequestRegistryElementC2SPacket {
            return SyncLibRequestRegistryElementC2SPacket(Data(registryId, elementIds))
        }
    }

    data class Data(
        val registryId: ResourceLocation,
        val elementIds: List<String>
    ) {
        companion object {
            private val ELEMENT_IDS_CODEC: StreamCodec<ByteBuf, List<String>> =
                ByteBufCodecs.collection(::ArrayList, ByteBufCodecs.STRING_UTF8)

            val STREAM_CODEC: StreamCodec<ByteBuf, Data> = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                Data::registryId,
                ELEMENT_IDS_CODEC,
                Data::elementIds,
                ::Data
            )

        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}