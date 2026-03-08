package com.github.kuripasanda

import com.github.kuripasanda.api.network.server.SyncLibInitializeS2CPacket
import com.github.kuripasanda.network.SyncLibClientNetworkHelper
import com.github.kuripasanda.synclib.client.event.ClientPlayerEvents

/**
 * クライアント側でサーバーから受け取ったデータを保持するためのオブジェクト。
 * [SyncLibInitializeS2CPacket]を受け取ると初期化され、サーバーから退出するまで保持されます。
 *
 * @see SyncLibInitializeS2CPacket
 * @see SyncLibClientNetworkHelper
 */
object SyncLibClientServerData {

    init {
        // サーバーから退出したときにデータをリセットするためのイベントリスナーを登録
        ClientPlayerEvents.SERVER_LEAVE.register { client, leftLevel -> reset() }
    }


    var serverId: String? = null
    var obfuscateKey: String? = null


    fun reset() {
        serverId = null
        obfuscateKey = null
    }

}