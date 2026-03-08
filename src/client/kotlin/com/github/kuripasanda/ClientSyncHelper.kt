package com.github.kuripasanda

import com.github.kuripasanda.api.network.SplitPacketUtil
import com.github.kuripasanda.api.network.client.SyncLibClientSyncCompleteC2SPacket
import com.github.kuripasanda.api.network.client.SyncLibRequestRegistryElementC2SPacket
import com.github.kuripasanda.api.sync.SyncHelper
import com.github.kuripasanda.mixin.client.ConnectScreenAccessor
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.round
import kotlin.system.measureTimeMillis
import kotlin.time.Clock
import kotlin.time.Instant

object ClientSyncHelper {

    /** 初回のデータ同期中であるかを示します */
    var isInitialSync: Boolean = false
        private set

    /* 初回の同期処理に関する変数 */
    private var startTime: Instant? = null
    private var registryHashes: ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<String, String>> = ConcurrentHashMap()
    private var receivedElements: ConcurrentLinkedQueue<ReceivedElement> = ConcurrentLinkedQueue()
    private var receivedAllElements = false
    private data class ReceivedElement(val registryId: ResourceLocation, val elementId: String, val elementData: ByteArray)
    private var integrityCheckThread: Thread? = null
    /* GUI表示用の変数 */
    private var requestedTotal: Long = 0
    private var receivedTotal: Long = 0
    private var receivedKiloBytes: Double = 0.0


    /* 初回以降の同期処理に関する変数 */
    private var elementRegisterThread: Thread = Thread {}


    init {
        // ClientConfigurationConnectionEvents.COMPLETE.register { handler, client -> reset() } 同期完了時は completeAllSyncTasks() 内でリセットする
        ClientConfigurationConnectionEvents.DISCONNECT.register { handler, client -> reset() }
    }

    fun startSync() {
        elementRegisterThread.interrupt()
        prepareRegistryElementRegisterThread()
        isInitialSync = true
        startTime = Clock.System.now()

        // 同期開始前にキャッシュからデータを読み込む
        Thread {
            try {
                ClientCacheHelper.loadFromCaches()
            }catch (e: Exception) {
                errorInSync(e)
            }
        }.also {
            it.name = "SyncLib Cache Loader"
            it.start()
        }

        SyncLib.LOGGER.info("[Sync] Starting synchronization process.")
    }

    fun reset() {
        isInitialSync = false
        startTime = null
        registryHashes = ConcurrentHashMap()
        requestedTotal = 0
        receivedTotal = 0
        receivedKiloBytes = 0.0
        receivedAllElements = false
        integrityCheckThread?.interrupt()
        SyncLibClient.connectScreenSubStatus = null
        SyncLib.LOGGER.info("[Sync] Reset synchronization state.")
    }


    fun onReceiveRegistryHashesPart(registryId: ResourceLocation, hashes: Map<String, String>) {
        // 全てのパートを送信したキーが送られたか確認
        if (registryId == SyncHelper.SEND_HASHES_COMPLETE_MESSAGE_KEY) {
            onCompleteReceiveRegistryHashes()
            return
        }

        SyncLibClient.connectScreenSubStatus = Component.translatable("synclib.gui.syncing.registry_hashes")

        registryHashes.putIfAbsent(registryId, ConcurrentHashMap())
        registryHashes[registryId]!!.putAll(hashes)
    }

    /** すべてのレジストリの全要素のハッシュ値のパートが受信されたときに呼び出される関数。ここで、受信したデータを処理できます。 */
    private fun onCompleteReceiveRegistryHashes() {
        SyncLibClient.connectScreenSubStatus = Component.translatable("synclib.gui.syncing.waiting_for_cache_loading")
        integrityCheckThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                // クライアント側キャッシュの読み込みが終わるまで待機
                if (!ClientCacheHelper.isLoaded.get()) {
                    Thread.sleep(100)
                    continue
                }

                onCompleteReceiveRegistryHashesImpl()
                break
            }
        }.also {
            it.name = "SyncLib IntegrityCheck"
            it.start()
        }
    }
    private fun onCompleteReceiveRegistryHashesImpl() {
        // 不足や不一致の要素を記録するためのマップ
        val requestRegistryElements = mutableMapOf<ResourceLocation, MutableList<String>>()
        val noNeedElements = mutableMapOf<ResourceLocation, List<String>>()

        SyncLib.LOGGER.info("[Sync] Perform data integrity checks.")
        SyncLibClient.connectScreenSubStatus = Component.translatable("synclib.gui.syncing.integrity_check")
        val checkingTime = measureTimeMillis {
            registryHashes.forEach { registryId, serverSideHashes ->
                val serverId = SyncLibClientServerData.serverId ?: throw IllegalStateException("Server ID is null.")
                val registry = SyncHelper.getRegistry(registryId)
                val clientSideHashes = registry?.calculateHashes()
                val clientSideKeysTemp = clientSideHashes?.keys?.toMutableList() ?: mutableListOf()
                if (clientSideHashes == null) { // クライアント側とサーバー側でレジストリが一致しない場合は例外を投げる
                    errorInSync(IllegalStateException("Registry $registryId is missing on the client side."))
                    return@forEach
                }

                // サーバー側のハッシュとクライアント側のハッシュを比較し、不足や不一致の要素を記録
                serverSideHashes.forEach { elementId, serverHash ->
                    val clientHash = clientSideHashes[elementId]
                    if (clientHash == null || clientHash != serverHash) {
                        requestRegistryElements.putIfAbsent(registryId, mutableListOf())
                        requestRegistryElements[registryId]!!.add(elementId)
                    }

                    // クライアント側のキーのリストから、サーバー側のハッシュに存在するキーを削除していく。クライアント側に存在するがサーバー側には存在しない要素を特定するため
                    clientSideKeysTemp.remove(elementId)
                }

                noNeedElements[registryId] = clientSideKeysTemp

                // 不要なデータを削除する
                if (noNeedElements.isNotEmpty()) {
                    clientSideKeysTemp.forEach { elementId ->
                        try {
                            registry.deleteCacheFile(serverId, elementId) // キャッシュファイルの削除
                        }catch (e:Exception) { SyncLib.LOGGER.error("[Sync] Failed to delete cache file for registry $registryId element $elementId: ${e.message}", e) }
                        registry.unregister(elementId)
                    }
                }
            }
        }

        // 不要な要素を削除したことをログに記録
        if (noNeedElements.isNotEmpty()) {
            SyncLib.LOGGER.info("[Sync] Found ${noNeedElements.values.sumOf { it.size.toLong() }} unnecessary elements across ${noNeedElements.size} registries. These elements have been removed from the client.")
        }

        if (requestRegistryElements.isEmpty()) {
            // すべての要素が一致している場合は、同期タスクを完了する
            SyncLib.LOGGER.info("[Sync] All registry elements are consistent. Synchronization complete. (${checkingTime}ms)")
            completeAllSyncTasks()
        } else {
            // 不足や不一致の要素がある場合は、サーバーにリクエストを送る
            SyncLib.LOGGER.warn("[Sync] Some registry elements are missing or mismatched. Requesting data for ${requestRegistryElements.values.sumOf { it.size.toLong() }} elements across ${requestRegistryElements.size} registries.")
            SyncLibClient.connectScreenSubStatus = Component.translatable("synclib.gui.syncing.requesting_data")
            requestRegistryElements.forEach { registryId, elementIds ->
                requestRegistryElements.forEach { registryId, requestIds ->
                    val split = SplitPacketUtil.splitStringList(requestIds)
                    split.forEach { splitIds ->
                        ClientConfigurationNetworking.send(SyncLibRequestRegistryElementC2SPacket.build(registryId, splitIds))
                    }
                }
            }
            requestedTotal = requestRegistryElements.values.sumOf { it.size.toLong() }

            // 全要素のリクエストが完了したことをサーバーに通知
            ClientConfigurationNetworking.send(SyncLibRequestRegistryElementC2SPacket.build(SyncHelper.REQUEST_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY, emptyList()))
        }
    }

    /** サーバーからレジストリ要素を受け取ったとき */
    fun onReceiveRegistryElement(registryId: ResourceLocation, elementId: String, elementData: ByteArray) {
        // 全要素が送信されたら
        if (registryId == SyncHelper.SEND_ALL_REGISTRY_ELEMENTS_COMPLETE_MESSAGE_KEY) {
            receivedAllElements = true
            return
        }

        // 受け取った要素をリストに追加。別スレッドで処理する
        receivedElements.add(ReceivedElement(registryId, elementId, elementData))
    }

    /** すべての同期タスクが完了したときに呼び出されます */
    private fun completeAllSyncTasks() {
        val totalTime = startTime?.let { Clock.System.now().minus(it).inWholeMilliseconds } ?: -1

        SyncLibClientServerData.serverId?.let { serverId ->
            ClientConfigurationNetworking.send(SyncLibClientSyncCompleteC2SPacket(serverId))
        }
        reset()

        SyncLib.LOGGER.info("[Sync] All synchronization tasks are complete. (Total time: ${totalTime}ms)")
    }


    /** 受け取ったレジストリ要素をクライアント側のレジストリに登録するためのスレッドを準備します。 */
    private fun prepareRegistryElementRegisterThread() {
        elementRegisterThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val serverId = SyncLibClientServerData.serverId

                // サーバーIDが取得できなければ停止する。
                if (serverId == null) {
                    if (isInitialSync) errorInSync(IllegalStateException("Server ID is null."))
                    Thread.interrupted()
                    break
                }

                val element = receivedElements.firstOrNull()
                if (element == null) {
                    // 初回の同期処理で、全データの受信が完了 & 受信したデータの処理が完了したら同期タスクを完了する
                    if (isInitialSync && receivedAllElements) {
                        completeAllSyncTasks()
                    }
                    Thread.sleep(500) // 要素がない場合は少し待ってから再度確認
                    continue
                }

                // 初回の同期時は、GUIの表示を更新する
                if (isInitialSync) {
                    receivedTotal++
                    receivedKiloBytes += element.elementData.size / 1024.0

                    val displayKiloBytes = round(receivedKiloBytes * 10.0) / 10.0
                    SyncLibClient.connectScreenSubStatus = Component.translatable("synclib.gui.syncing.receive_data", receivedTotal, requestedTotal, displayKiloBytes)
                }

                registerReceivedElement(serverId, element)
            }
        }.also {
            it.name = "SyncLib Data Receiver"
            it.start()
        }
    }
    private fun registerReceivedElement(serverId: String, element: ReceivedElement) {
        try {
            val registry = SyncHelper.getRegistry(element.registryId) ?: throw IllegalStateException("Registry ${element.registryId} not found on client side.")
            registry.registerFromByteArray(element.elementId, element.elementData)
            registry.saveCacheToFile(serverId, element.elementId, EnvType.CLIENT)
        }catch (e:Exception) {
            errorInSync(e)
        }finally {
            receivedElements.remove(element)
        }
    }

    @Synchronized
    private fun errorInSync(e: Exception) {
        Minecraft.getInstance().execute {
            val currentScreen = Minecraft.getInstance().screen
            if (currentScreen is ConnectScreen) {
                val connectScreen = (currentScreen as ConnectScreenAccessor)

                val channelFuture = connectScreen.channelFuture
                val connection = connectScreen.connection

                channelFuture?.cancel(true)
                connection?.disconnect(SyncHelper.errorDisconnectMessage)
            }

            val screen = DisconnectedScreen(
                JoinMultiplayerScreen(TitleScreen()),
                Component.translatable("synclib.gui.syncing.error"),
                SyncHelper.errorDisconnectMessage
            )
            Minecraft.getInstance().disconnect(screen)
        }
        SyncLib.LOGGER.error("[Sync] An error occurred during synchronization: ${e.message}", e)
    }




}