package com.github.kuripasanda

import com.github.kuripasanda.api.network.SyncLibNetworkHelper
import com.github.kuripasanda.api.obfuscate.EasyObfuscatorImpl
import com.github.kuripasanda.api.obfuscate.Obfuscator
import com.github.kuripasanda.api.sync.SyncHelper
import com.github.kuripasanda.config.SyncLibConfigs
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.MessageDigest

object SyncLib : ModInitializer {

	const val MOD_ID = "synclib"
	val LOGGER = LoggerFactory.getLogger("synclib")
	val digest = MessageDigest.getInstance("SHA-256")
	var cacheDir: Path? = null

	lateinit var server: MinecraftServer private set
	lateinit var obfuscator: Obfuscator private set

	fun setObfuscateKey(key: String) {
		obfuscator = EasyObfuscatorImpl(key)
	}

	override fun onInitialize() {
		LOGGER.info("Initializing SyncLib...")

		// 設定の初期化
		SyncLibConfigs

		ServerLifecycleEvents.SERVER_STARTING.register { server ->
			this.server = server

			cacheDir = server.serverDirectory.resolve("cache/${MOD_ID}/")
		}

		// 難読化ツールの初期化
		setObfuscateKey(SyncLibConfigs.serverConfig.obfuscateKey())

		// ネットワーキングの初期化
		SyncLibNetworkHelper.initConfigurationPhaseServerPackets()
		SyncLibNetworkHelper.initConfigurationPhaseServerListeners()

		// 同期機能の初期化
		SyncHelper
	}

}