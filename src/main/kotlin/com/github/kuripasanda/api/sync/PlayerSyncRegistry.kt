package com.github.kuripasanda.api.sync

import com.github.kuripasanda.SyncLib
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

/**
 * プレイヤー別で同期可能なデータのレジストリ
 * @param T 同期対象のデータの型
 * @param id レジストリの識別子。通常は 「modid:registry_name」 の形式で指定されます。
 * @param serializer 同期対象のデータをシリアライズ/デシリアライズするためのKSerializer。
 * @param obfuscatedClientSide データをキャッシュする際にクライアント側で難読化するかどうか。trueの場合、キャッシュファイルに保存されるデータは難読化されます。falseの場合、キャッシュファイルに保存されるデータは平文のJSON形式になります。
 * @param onRegister データがレジストリに登録される前に呼び出されるコールバック関数。ここで登録されるデータを編集できます。
 * @param onUnregister データがレジストリから削除されたときに呼び出されるコールバック関数。
 */
class PlayerSyncRegistry<T: Any>(
    id: ResourceLocation,
    serializer: KSerializer<T>,
    obfuscatedClientSide: Boolean,
    var onRegister: (PlayerSyncRegistry<T>, UUID, String, T) -> T,
    var onUnregister: (UUID, T) -> Unit
): AbstractSyncRegistry<T>(id, serializer, obfuscatedClientSide) {

    /** 同期対象のデータのマップ */
    private val dataMap: ConcurrentHashMap<UUID, ConcurrentHashMap<String, T>> = ConcurrentHashMap()


    /** プレイヤーのレジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。 */
    fun register(playerUUID: UUID, key: String, data: T) {
        val result = onRegister.invoke(this, playerUUID, key, data)
        val playerDataMap = dataMap.computeIfAbsent(playerUUID) { ConcurrentHashMap() }
        playerDataMap[key] = data

        // データが登録された後の管理用コールバックを呼び出す
        SyncLib.playerRegistryOnRegisterForManagement.invoke(this, playerUUID, key, result)
    }

    /**
     * プレイヤーのレジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。
     * ※主にSyncLib内部で使用するメソッドです。
     */
    @Environment(EnvType.CLIENT)
    override fun registerFromByteArray(key: String, bytes: ByteArray) {
        val data = fromByteArray(bytes)
        register(getPlayerUUID(), key, data)
    }

    /** プレイヤーのレジストリにキャッシュファイルからデータを読み込んで登録します。既に同じキーが存在する場合は上書きされます。
     * キャッシュファイルが存在しない場合や、キャッシュファイルの内容が無効な場合は例外がスローされます。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @param environment 実行している環境
     * @throws NoSuchFileException 指定されたキーのキャッシュファイルが存在しない場合、またはキャッシュファイルが有効なファイルでない場合にスローされます。
     * @throws SerializationException 復号特有の誤りが発生した場合
     * @throws IllegalArgumentException デコード入力が有効なインスタンスでない場合
     */
    @Environment(EnvType.CLIENT)
    override fun registerFromCacheFile(serverId: String, key: String, environment: EnvType) {
        val data = loadCacheFromFile(serverId, key, environment)
        register(getPlayerUUID(), key, data)
    }

    /** プレイヤーのレジストリからデータをキーで削除します。キーが存在しない場合は何も行いません。 */
    fun unregister(playerUUID: UUID, key: String) {
        val data = dataMap[playerUUID]?.remove(key)
        if (data != null) onUnregister.invoke(playerUUID, data)
    }

    /** プレイヤーのレジストリ内のデータをキーで取得します。キーが存在しない場合はnullを返します。 */
    fun get(playerUUID: UUID, key: String): T? = dataMap[playerUUID]?.get(key)

    /** プレイヤーのレジストリ内のすべてのデータを取得します。 */
    fun getAll(playerUUID: UUID): Map<String, T> = dataMap[playerUUID]?.toMap() ?: emptyMap()

    /** プレイヤーのレジストリ内のデータの数を返します。 */
    fun getSize(playerUUID: UUID): Int = dataMap[playerUUID]?.size ?: 0

    /** レジストリ内に指定されたキーが存在するかどうかを確認します。 */
    fun contains(playerUUID: UUID, key: String): Boolean = dataMap[playerUUID]?.containsKey(key) ?: false

    /** 全プレイヤーのレジストリ内のすべてのデータをクリアします。 */
    override fun clear() { dataMap.clear() }

    /** プレイヤーのレジストリ内のすべてのデータをクリアします。 */
    fun clearPlayer(playerUUID: UUID) { dataMap[playerUUID]?.clear() }

    /** プレイヤーのレジストリ内のすべてのデータのハッシュを計算して返します。キーとハッシュのマップとして返されます。 */
    fun calculateHashes(playerUUID: UUID): Map<String, String> {
        val result = hashMapOf<String, String>()
        dataMap[playerUUID]?.forEach { (key, value) ->
            result[key] = calculateHash(value)
        }
        return result
    }

    /**
     * キーで指定されたレジストリ内の要素をキャッシュとしてファイルに保存します。
     * 難読化が有効になっていれば、保存するキャッシュデータは難読化処理を施したものになります。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @param environment 実行している環境
     * @throws NoSuchElementException 指定されたキーがレジストリ内に存在しない場合にスローされます。
     */
    @Environment(EnvType.CLIENT)
    @Throws(NoSuchElementException::class)
    override fun saveCacheToFile(serverId: String, key: String, environment: EnvType) {
        val playerUUID = getPlayerUUID()
        val element = dataMap[playerUUID]?.get(key) ?: throw NoSuchElementException("Key '$key' not found in registry '${id}'.")
        saveCacheToFileInternal(serverId, key, element, environment)
    }

    /**
     * プレイヤーのキーで指定されたレジストリ内の要素をバイト配列にシリアライズして返します。要素が存在しない場合はnullを返します。
     * シリアライズされたデータはJson形式の文字列をバイト配列に変換したものになります。
     */
    fun getAsByteArray(playerUUID: UUID, key: String): ByteArray? {
        val data = dataMap[playerUUID]?.get(key) ?: return null
        val json = Json.encodeToString(serializer, data)
        return json.toByteArray()
    }

    /**
     * プレイヤーにレジストリの要素を同期します。
     * @param player 同期対象のプレイヤーを指定します。
     * @param key レジストリ内の要素のキーを指定します。
     * @throws NoSuchElementException 指定されたキーがレジストリ内に存在しない場合にスローされます。
     */
    @Environment(EnvType.SERVER)
    @Throws(NoSuchElementException::class)
    override fun syncToPlayer(player: ServerPlayer, key: String) {
        val playerUUID = player.uuid
        if (contains(playerUUID, key).not()) throw NoSuchElementException("Key '$key' not found in registry '${id}' for player '$playerUUID'.")
        SyncHelper.sendRegistryElement(player, this, key)
    }



    /** プレイヤーのUUIDを取得します。失敗した場合は[IllegalStateException]がスローされます。 */
    @Environment(EnvType.CLIENT)
    @Throws(IllegalStateException::class)
    fun getPlayerUUID(): UUID = SyncLib.playerUUID ?: throw IllegalStateException("Player UUID is null")

}