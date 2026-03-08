package com.github.kuripasanda.config

import com.github.kuripasanda.synclib.config.SyncLibServerConfig

object SyncLibConfigs {

    /** サーバー側のコンフィグ */
    val serverConfig: SyncLibServerConfig = SyncLibServerConfig.createAndLoad()

}