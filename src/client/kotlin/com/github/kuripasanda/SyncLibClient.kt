package com.github.kuripasanda

import com.github.kuripasanda.network.SyncLibClientNetworkHelper
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object SyncLibClient : ClientModInitializer {

	/** マルチプレイヤー参加画面のサブステータス。SyncLibの同期のステータス表示に使用します */
    @set:Synchronized
	var connectScreenSubStatus: Component? = null

	override fun onInitializeClient() {
		SyncLib.cacheDir = Minecraft.getInstance().gameDirectory.resolve("cache/${SyncLib.MOD_ID}/").toPath()

		// ネットワーキングの初期化
		SyncLibClientNetworkHelper

		// クライアント側のキャッシュ管理の初期化
		ClientCacheHelper
	}

}