package com.github.kuripasanda.api.network

import com.github.kuripasanda.api.network.client.SyncLibClientSyncCompleteC2SPacket
import com.github.kuripasanda.api.network.client.SyncLibRequestRegistryElementC2SPacket
import com.github.kuripasanda.api.network.server.SyncLibInitializeS2CPacket
import com.github.kuripasanda.api.network.server.SyncLibRegistryHashesS2CPacket
import com.github.kuripasanda.api.sync.SyncHelper
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking

object SyncLibNetworkHelper {

    /** ネットワーキングの初期化。パケットの登録などを行います */
    fun initConfigurationPhaseServerPackets() {
        SyncLibInitializeS2CPacket
        SyncLibRegistryHashesS2CPacket
    }

    /** ネットワーキングのイベントリスナーの初期化。クライアントからのパケット受信などのイベントを登録します */
    fun initConfigurationPhaseServerListeners() {

        // C → S | 同期完了
        ServerConfigurationNetworking.registerGlobalReceiver(SyncLibClientSyncCompleteC2SPacket.ID) { packet, context ->
            SyncHelper.handleReceiveSyncPacket(SyncHelper.ReceivePacketType.COMPLETE, packet, context)
        }

        // C → S | データ要求
        ServerConfigurationNetworking.registerGlobalReceiver(SyncLibRequestRegistryElementC2SPacket.ID) { packet, context ->
            SyncHelper.handleReceiveSyncPacket(SyncHelper.ReceivePacketType.REQUEST_DATA, packet, context)
        }

    }


}