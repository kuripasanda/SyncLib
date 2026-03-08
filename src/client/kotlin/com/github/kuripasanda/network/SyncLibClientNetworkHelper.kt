package com.github.kuripasanda.network

import com.github.kuripasanda.ClientSyncHelper
import com.github.kuripasanda.SyncLib
import com.github.kuripasanda.SyncLibClientServerData
import com.github.kuripasanda.api.network.server.SyncLibInitializeS2CPacket
import com.github.kuripasanda.api.network.server.SyncLibRegistryElementS2CPacket
import com.github.kuripasanda.api.network.server.SyncLibRegistryHashesS2CPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking

object SyncLibClientNetworkHelper {

    init {
        // サーバーから初期化パケットを受け取るためのレシーバー
        ClientConfigurationNetworking.registerGlobalReceiver(SyncLibInitializeS2CPacket.ID) { packet, _ ->
            val data = packet.data

            SyncLibClientServerData.serverId = data.serverId
            SyncLibClientServerData.obfuscateKey = data.obfuscateKey
            SyncLib.setObfuscateKey(data.obfuscateKey)

            ClientSyncHelper.startSync()

            SyncLib.LOGGER.info("[Sync] Received SyncLib initialization packet (ServerID: ${data.serverId}). Starting synchronization process...")
        }

        // サーバーからレジストリのハッシュ値のパートを受け取るためのレシーバー
        ClientConfigurationNetworking.registerGlobalReceiver(SyncLibRegistryHashesS2CPacket.ID) { packet, _ ->
            ClientSyncHelper.onReceiveRegistryHashesPart(packet.data.registryId, packet.data.hashes)
        }

        // サーバーからレジストリ要素のデータを受け取るためのレシーバー
        ClientConfigurationNetworking.registerGlobalReceiver(SyncLibRegistryElementS2CPacket.ID) { packet, _ ->
            ClientSyncHelper.onReceiveRegistryElement(packet.registryId, packet.elementKey, packet.registryElement)
        }
    }

}