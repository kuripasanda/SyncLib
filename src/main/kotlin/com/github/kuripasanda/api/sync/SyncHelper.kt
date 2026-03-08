package com.github.kuripasanda.api.sync

import com.github.kuripasanda.SyncLib
import com.github.kuripasanda.api.network.SplitPacketUtil
import com.github.kuripasanda.api.network.client.SyncLibRequestRegistryElementC2SPacket
import com.github.kuripasanda.api.network.server.SyncLibInitializeS2CPacket
import com.github.kuripasanda.api.network.server.SyncLibRegistryElementS2CPacket
import com.github.kuripasanda.api.network.server.SyncLibRegistryHashesS2CPacket
import com.github.kuripasanda.config.SyncLibConfigs
import kotlinx.serialization.KSerializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.util.CommonColors
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.system.measureTimeMillis

/**
 * データ同期のヘルパークラス
 */
object SyncHelper {

    /** クライアントから受け取ったパケットの種類 */
    enum class ReceivePacketType { REQUEST_DATA, COMPLETE }

    /** サーバーがクライアントに全てのレジストリのハッシュ値の送信を完了したことを伝えるためのキー */
    val SEND_HASHES_COMPLETE_MESSAGE_KEY = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "send_hashes_complete")

    /** クライアントがレジストリ要素を全て要求したことを伝えるためのキー */
    val REQUEST_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "request_registry_elements_complete")

    /** サーバーがレジストリ要素の全ての要求の対応に完了したことを伝えるためのキー */
    val SEND_ALL_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY = ResourceLocation.fromNamespaceAndPath(SyncLib.MOD_ID, "send_all_registry_elements_complete")


    /** 同期対象のレジストリのマップ */
    private val registries = ConcurrentHashMap<ResourceLocation, SyncRegistry<*>>()

    /** プレイヤーごとの同期状態を管理するマップ */
    private val syncStatus = ConcurrentHashMap<UUID, SyncStatus>()

    init {
        // プレイヤーが参加を要求後、サーバーから様々なデータを送信する際のイベント
        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, server ->
            try {
                val owner = handler.owner

                // 同期状態を初期化
                val syncTask = SyncConfigurationTask(owner.id, handler)
                syncStatus.put(owner.id, syncTask.status)

                // 同期を開始
                handler.addTask(syncTask)
            }catch (e: Exception) {
                errorInSyncing(handler, e)
            }
        }
        ServerConfigurationConnectionEvents.DISCONNECT.register { handler, server ->
            // プレイヤーが切断されたときに同期状態をクリーンアップ
            val status = syncStatus[handler.owner.id]
            if (status != null) {
                SyncLib.LOGGER.info("[Sync] Player with UUID ${handler.owner.id} disconnected. Cleaning up sync status.")
                status.task.stop()
                syncStatus.remove(handler.owner.id)
            }
        }
    }


    /**
     * 新しい同期レジストリを作成します
     * @param id レジストリの識別子。通常は 「modid:registry_name」 の形式で指定されます
     * @param serializer 同期対象のデータをシリアライズ/デシリアライズするためのKSerializer
     * @param obfuscatedServerSide 同期対象のデータがサーバー側で難読化されているか・すべきかどうかを示すフラグ
     * @param obfuscatedClientSide 同期対象のデータがクライアント側で難読化されているか・すべきかどうかを示すフラグ
     * @param onRegister データがレジストリに登録される前に呼び出されるコールバック関数。登録されるデータを編集することができます。
     * @param onUnregister データがレジストリから削除されたときに呼び出されるコールバック関数。
     * @return 作成されたSyncRegistryのインスタンス
     */
    fun <T: Any> createRegistry(
        id: ResourceLocation,
        serializer: KSerializer<T>,
        obfuscatedServerSide: Boolean,
        obfuscatedClientSide: Boolean,
        onRegister: (T) -> T = { it },
        onUnregister: (T) -> Unit = {}
    ): SyncRegistry<T> {
        val registry = SyncRegistry<T>(id, serializer, obfuscatedServerSide, obfuscatedClientSide, onRegister, onUnregister)
        registries.put(id, registry)
        return registry
    }

    /**
     * 指定されたIDの同期レジストリを取得します
     * @param id レジストリの識別子
     * @return 指定されたIDの同期レジストリ。存在しない場合はnullを返します。
     */
    fun getRegistry(id: ResourceLocation): SyncRegistry<*>? = registries[id]

    /**
     * 登録されているすべての同期レジストリを取得します
     * @return 登録されているすべての同期レジストリのマップ。キーはレジストリの識別子、値は対応するSyncRegistryのインスタンスです。
     */
    fun getAllRegistries(): Map<ResourceLocation, SyncRegistry<*>> = HashMap(registries)

    /**
     * 指定されたサーバーIDに対応するキャッシュディレクトリのパスを取得します。
     * @param serverId サーバーID
     */
    fun getCacheDir(serverId: String): Path? = SyncLib.cacheDir?.resolve("${serverId}/registries/")


    /**
     * クライアントから同期関連のパケットを受け取った際の処理
     * @param type 受け取ったパケットの種類
     * @param packet 受け取ったパケット
     */
    @Environment(EnvType.SERVER)
    fun handleReceiveSyncPacket(type: ReceivePacketType, packet: CustomPacketPayload, context: ServerConfigurationNetworking.Context) {
        val owner = context.networkHandler().owner
        val status = syncStatus[owner.id] ?: throw IllegalStateException("No sync status found for player with UUID ${owner.id}")
        try {
            val task = status.task

            when (type) {
                ReceivePacketType.REQUEST_DATA -> task.onRequestRegistryElements(owner.id, packet)
                ReceivePacketType.COMPLETE -> task.handleSyncComplete(owner.id)
            }
        }catch (e: Exception) {
            errorInSyncing(status.handler, e)
        }
    }


    /* ========== 内部クラス・メソッド ========== */

    @Environment(EnvType.SERVER)
    private class SyncConfigurationTask(
        val uuid: UUID,
        val handler: ServerConfigurationPacketListenerImpl
    ): ConfigurationTask {
        companion object {
            val KEY = ConfigurationTask.Type("${SyncLib.MOD_ID}:sync")
        }

        /** 同期の状態を管理するオブジェクト */
        val status: SyncStatus

        /** クライアントから要求されたレジストリ要素 */
        val requestedRegistryElements: MutableMap<ResourceLocation, MutableList<String>> = mutableMapOf()

        private var sendingDataThread: Thread? = null


        init {
            val sendRegistries = registries.keys.toList()
            status = SyncStatus(sendRegistries, this, handler)
        }

        override fun type(): ConfigurationTask.Type = KEY

        fun stop() {
            try {
                sendingDataThread?.interrupt()
            }catch (e: Exception) {
                SyncLib.LOGGER.warn("[Sync] Failed to interrupt sending data thread for player with UUID $uuid: ${e.message}", e)
            }
        }

        /** 同期タスクを開始 */
        override fun start(sendPacket: Consumer<Packet<*>>) {
            SyncLib.LOGGER.info("[Sync] Starting synchronization for player with UUID $uuid. Sending ${status.sendRegistries.size} registries.")
            try {
                val serverId = SyncLibConfigs.serverConfig.serverId()

                // 初期化のためのパケットを送信
                val initializePacket = SyncLibInitializeS2CPacket(SyncLibInitializeS2CPacket.Data(
                    serverId = serverId,
                    obfuscateKey = SyncLibConfigs.serverConfig.obfuscateKey()
                ))
                sendPacket.accept(ServerConfigurationNetworking.createS2CPacket(initializePacket))

                // クライアントにレジストリのハッシュ値を送信
                sendRegistryHashes(uuid, sendPacket)

                // 全てのレジストリのハッシュ値の送信が完了したことをクライアントに通知
                sendPacket.accept(ServerConfigurationNetworking.createS2CPacket(SyncLibRegistryHashesS2CPacket(
                    SyncLibRegistryHashesS2CPacket.Data(SEND_HASHES_COMPLETE_MESSAGE_KEY, emptyMap())
                )))

                SyncLib.LOGGER.info("[Sync] Completed sending registry hashes to player with UUID $uuid.")

            }catch (e: Exception) {
                errorInSyncing(handler, e)
            }
        }

        /** クライアントから特定のレジストリ要素のデータを要求されたときに呼び出されるメソッド
         * 通常、レジストリ要素の要求は複数のパートに分割されてクライアントから送信されます。
         */
        @Environment(EnvType.SERVER)
        fun onRequestRegistryElements(uuid: UUID, packet: CustomPacketPayload) {
            val requestPacket = packet as SyncLibRequestRegistryElementC2SPacket
            val registryId = requestPacket.data.registryId
            val elementIds = requestPacket.data.elementIds

            // クライアント側からのレジストリ要素の要求が完了したか確認
            if (registryId == REQUEST_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY) {
                sendingDataThread = Thread {
                    val totalElements = requestedRegistryElements.values.sumOf { it.size.toLong() }
                    SyncLib.LOGGER.info("[Sync] Sending the requested data to $uuid for $totalElements elements across ${requestedRegistryElements.size} registries.")
                    val time = measureTimeMillis {
                        requestedRegistryElements.forEach { requestRegistryId, requestElementIds ->
                            val registry = registries[requestRegistryId] ?: throw IllegalStateException("Requested registry $requestRegistryId not found.")
                            requestElementIds.forEach { elementId ->
                                val elementByteArray = registry.getAsByteArray(elementId) ?: throw IllegalStateException("Requested element $elementId not found in registry $requestRegistryId.")
                                val packet = SyncLibRegistryElementS2CPacket(requestRegistryId, elementId, elementByteArray)
                                status.handler.send(ServerConfigurationNetworking.createS2CPacket(packet))
                            }
                        }
                        // サーバーが要求された全要素の送信を完了したことをクライアントに通知
                        val completePacket = ServerConfigurationNetworking.createS2CPacket(SyncLibRegistryElementS2CPacket(SEND_ALL_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY, "", ByteArray(0)))
                        status.handler.send(completePacket)
                    }
                    SyncLib.LOGGER.info("[Sync] Completed sending all requested registry elements. (Total time: ${time}ms | $totalElements elements)")
                }
                sendingDataThread!!.name = "SyncLib SendingData"
                sendingDataThread!!.start()

                return // キーメッセージは偽RegistryIdが送信されるため、以降の処理をスキップ
            }

            requestedRegistryElements.putIfAbsent(registryId, mutableListOf())
            requestedRegistryElements[registryId]!!.addAll(elementIds)
        }

        @Environment(EnvType.SERVER)
        private fun sendRegistryHashes(uuid: UUID, sendPacket: Consumer<Packet<*>>) {
            val status = syncStatus[uuid] ?: return
            try {
                // 全てのレジストリの個々のデータのハッシュ値を計算して、クライアントに送信する
                val hashes = LinkedHashMap<ResourceLocation, Map<String, String>>()
                status.sendRegistries.forEach { id ->
                    val registry = registries[id] ?: return@forEach
                    val registryHashes = registry.calculateHashes()
                    hashes[id] = registryHashes
                }

                // 各レジストリのハッシュ値を分割して送信する
                hashes.forEach { registryId, registryHashes ->
                    val splitHashes = SplitPacketUtil.splitStringMap(registryHashes)

                    splitHashes.forEach { splitPairs ->
                        val splitHashes: Map<String, String> = splitPairs.associate { it.first to it.second }
                        val packet = SyncLibRegistryHashesS2CPacket(SyncLibRegistryHashesS2CPacket.Data(registryId, splitHashes))
                        sendPacket.accept(ServerConfigurationNetworking.createS2CPacket(packet))
                    }

                }
            }catch (e: Exception) {
                errorInSyncing(status.handler, e)
            }
        }

        /** クライアントとの同期が完了したときに呼び出されるメソッド */
        @Environment(EnvType.SERVER)
        fun handleSyncComplete(uuid: UUID) {
            val status = syncStatus[uuid] ?: return

            // マイクラに同期タスクの完了を通知する
            status.handler.completeTask(this.type())

            // 同期状態をクリーンアップ
            syncStatus.remove(uuid)

            SyncLib.LOGGER.info("[Sync] Synchronization complete for player with UUID $uuid.")
        }

    }

    @Environment(EnvType.SERVER)
    private class SyncStatus(
        var sendRegistries: List<ResourceLocation>,
        val task: SyncConfigurationTask,
        val handler: ServerConfigurationPacketListenerImpl
    )

    /** 同期中にエラーが発生したときの切断メッセージ */
    val errorDisconnectMessage: Component = Component.literal("[SyncLib] ").withColor(CommonColors.SOFT_RED)
        .append(Component.translatable("synclib.error.sync.1").withColor(CommonColors.SOFT_YELLOW))
        .append(Component.translatable("synclib.error.sync.2").withColor(CommonColors.SOFT_YELLOW))
    /** 同期中にエラーが発生したときの処理 */
    @Environment(EnvType.SERVER)
    private fun errorInSyncing(handler: ServerConfigurationPacketListenerImpl, e: Exception) {
        handler.disconnect(errorDisconnectMessage)
        syncStatus.remove(handler.owner.id)
        SyncLib.LOGGER.error("[Sync] Error while starting synchronization for player with UUID ${handler.owner.id}: ${e.message}", e)
    }

}
