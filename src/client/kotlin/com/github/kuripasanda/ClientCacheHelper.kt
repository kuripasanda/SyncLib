package com.github.kuripasanda

import com.github.kuripasanda.api.sync.SyncHelper
import com.github.kuripasanda.synclib.client.event.ClientPlayerEvents
import net.fabricmc.api.EnvType
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.measureTimeMillis

object ClientCacheHelper {

    val isLoaded: AtomicReference<Boolean> = AtomicReference(false)

    init {
        // クライアントがサーバーから切断されたときにキャッシュをリセットするイベントリスナーを登録
        ClientPlayerEvents.SERVER_LEAVE.register { client, leftLevel ->
            reset()
        }
    }

    /** キャッシュをリセットします。全てのレジストリのデータをクリアし、キャッシュからの読み込み状態をリセットします。 */
    fun reset() {
        isLoaded.set(false)
        SyncHelper.getAllRegistries().forEach { _, registry ->
            registry.clear()
        }
        SyncLib.LOGGER.info("[Cache] Cache reset! All registry data cleared.")
    }

    /** キャッシュからレジストリのデータを読み込みます。 */
    fun loadFromCaches() {
        val serverId = SyncLibClientServerData.serverId ?: throw IllegalStateException("Server ID is null")
        val cacheDir = SyncHelper.getCacheDir(serverId)!!

        SyncLib.LOGGER.info("[Cache] Loading registry elements from caches....")
        SyncHelper.getAllRegistries().forEach { registryId, registry ->
            val registryDir = cacheDir.resolve("${registryId.namespace}/${registryId.path}")
            if (!registryDir.exists() || !registryDir.isDirectory())  {
                SyncLib.LOGGER.warn("[Cache] Cache directory for registry $registryId does not exist or is not a directory: ${registryDir.toAbsolutePath()}")
                return@forEach
            }

            val elementFiles = registryDir.toFile().listFiles { file -> file.isFile && file.name.endsWith(".cache") } ?: arrayOf()
            elementFiles.forEach { file ->
                try {
                    val elementId = file.name.removeSuffix(".cache")
                    registry.registerFromCacheFile(serverId, elementId, EnvType.CLIENT)
                }catch (e: Exception) {
                    //SyncLib.LOGGER.error("[Cache] Failed to load registry element from cache file: ${file.absolutePath}", e)
                    // ログが大量に出力されるため、無視
                }
            }
        }
        SyncLib.LOGGER.info("[Cache] Loaded registry elements from caches!")

        isLoaded.set(true)
    }

}