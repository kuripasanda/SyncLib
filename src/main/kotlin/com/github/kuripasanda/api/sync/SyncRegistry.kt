package com.github.kuripasanda.api.sync

import com.github.kuripasanda.SyncLib
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

/**
 * 同期可能なデータのレジストリ
 * @param T 同期対象のデータの型
 * @param id レジストリの識別子。通常は 「modid:registry_name」 の形式で指定されます。
 * @param serializer 同期対象のデータをシリアライズ/デシリアライズするためのKSerializer。
 * @param obfuscatedClientSide データをキャッシュする際にクライアント側で難読化するかどうか。trueの場合、キャッシュファイルに保存されるデータは難読化されます。falseの場合、キャッシュファイルに保存されるデータは平文のJSON形式になります。
 * @param onRegister データがレジストリに登録される前に呼び出されるコールバック関数。ここで登録されるデータを編集できます。
 * @param onUnregister データがレジストリから削除されたときに呼び出されるコールバック関数。
 */
class SyncRegistry<T: Any>(
    val id: ResourceLocation,
    val serializer: KSerializer<T>,
    val obfuscatedClientSide: Boolean,
    var onRegister: (T) -> T,
    var onUnregister: (T) -> Unit
) {

    /** 同期対象のデータのマップ */
    private val dataMap: ConcurrentHashMap<String, T> = ConcurrentHashMap()


    /** レジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。 */
    fun register(key: String, data: T) {
        val result = onRegister.invoke(data)
        dataMap[key] = result
    }

    /**
     * レジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。
     * ※主にSyncLib内部で使用するメソッドです。
     */
    @Environment(EnvType.CLIENT)
    fun registerFromByteArray(key: String, bytes: ByteArray) {
        val data = fromByteArray(bytes)
        register(key, data)
    }

    /** レジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。
     * キャッシュファイルからデータを読み込んで登録します。キャッシュファイルが存在しない場合や、キャッシュファイルの内容が無効な場合は例外がスローされます。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @param environment 実行している環境
     * @throws NoSuchFileException 指定されたキーのキャッシュファイルが存在しない場合、またはキャッシュファイルが有効なファイルでない場合にスローされます。
     * @throws SerializationException 復号特有の誤りが発生した場合
     * @throws IllegalArgumentException デコード入力が有効なインスタンスでない場合
     */
    @Environment(EnvType.CLIENT)
    fun registerFromCacheFile(serverId: String, key: String, environment: EnvType) {
        val data = loadCacheFromFile(serverId, key, environment)
        register(key, data)
    }

    /** レジストリからデータをキーで削除します。キーが存在しない場合は何も行いません。 */
    fun unregister(key: String) {
        val data = dataMap.remove(key)
        if (data != null) onUnregister.invoke(data)
    }

    /** レジストリ内のデータをキーで取得します。キーが存在しない場合はnullを返します。 */
    fun get(key: String): T? = dataMap[key]

    /** レジストリ内のすべてのデータを取得します。 */
    fun getAll(): Map<String, T> = dataMap.toMap()

    /** レジストリ内のデータの数を返します。 */
    fun getSize(): Int = dataMap.size

    /** レジストリ内に指定されたキーが存在するかどうかを確認します。 */
    fun contains(key: String): Boolean = dataMap.containsKey(key)

    /** レジストリ内のすべてのデータをクリアします。 */
    fun clear() {
        dataMap.clear()
    }

    /** レジストリ内のすべてのデータのハッシュを計算して返します。キーとハッシュのマップとして返されます。 */
    fun calculateHashes(): Map<String, String> {
        val result = hashMapOf<String, String>()
        dataMap.forEach { (key, value) ->
            result[key] = calculateHash(value)
        }
        return result
    }

    /** データのハッシュ値を計算します。データはJson形式でシリアライズされ、そのバイト列のSHA-256ハッシュが計算されます。 */
    fun calculateHash(key: String): String? {
        val data = dataMap[key] ?: return null
        return calculateHash(data)
    }

    /** データのハッシュ値を計算します。データはJson形式でシリアライズされ、そのバイト列のSHA-256ハッシュが計算されます。 */
    fun calculateHash(data: T): String {
        val serialized = Json.encodeToString(serializer, data)
        val hashBytes = SyncLib.digest.digest(serialized.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
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
    fun saveCacheToFile(serverId: String, key: String, environment: EnvType) {
        val element = dataMap[key] ?: throw NoSuchElementException("Key '$key' not found in registry '${id}'.")
        val cacheDir = SyncHelper.getCacheDir(serverId)!!.resolve("${id.namespace}/${id.path}/")
        cacheDir.toFile().mkdirs()

        val cacheFile = cacheDir.resolve("$key.cache").toFile()
        val jsonPlaneText = Json.encodeToString(serializer, element)
        val needObfuscation = if (environment == EnvType.SERVER) false else obfuscatedClientSide
        val finalText = if (needObfuscation) SyncLib.obfuscator.obfuscate(jsonPlaneText) else jsonPlaneText
        cacheFile.writeText(finalText)
    }

    /**
     * キーで指定されたレジストリ内の要素のキャッシュファイルを読み込みます。
     * 難読化が有効になっていれば、読み込んだキャッシュデータは復号処理を施したものになります。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @param environment 実行している環境
     * @throws NoSuchFileException 指定されたキーのキャッシュファイルが存在しない場合、またはキャッシュファイルが有効なファイルでない場合にスローされます。
     * @throws SerializationException 復号特有の誤りが発生した場合
     * @throws IllegalArgumentException デコード入力が有効なインスタンスでない場合
     */
    @Environment(EnvType.CLIENT)
    @Throws(NoSuchFileException::class, SerializationException::class, IllegalArgumentException::class)
    fun loadCacheFromFile(serverId: String, key: String, environment: EnvType): T {
        val cacheDir = SyncHelper.getCacheDir(serverId)!!.resolve("${id.namespace}/${id.path}/")
        val cacheFile = cacheDir.resolve("$key.cache").toFile()
        if (!cacheFile.exists()) throw NoSuchFileException(cacheFile, reason = "Cache file for key '$key' not found in registry '${id}'.")
        if (!cacheFile.isFile) throw NoSuchFileException(cacheFile, reason = "Cache file for key '$key' in registry '${id}' is not a file.")

        val cachedText = cacheFile.readText()
        val needObfuscation = if (environment == EnvType.SERVER) false else obfuscatedClientSide
        val finalText = if (needObfuscation) SyncLib.obfuscator.deobfuscate(cachedText) else cachedText
        return Json.decodeFromString(serializer, finalText)
    }

    /**
     * キーで指定されたレジストリ要素のキャッシュファイルを削除します。
     * ※今レジストリに登録されていなくても、キャッシュファイルが存在する場合は削除を試みます。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @throws IOException ファイルの書き込みに失敗した場合にスローされます。
     */
    @Throws(NoSuchElementException::class, IOException::class)
    fun deleteCacheFile(serverId: String, key: String) {
        val cacheDir = SyncHelper.getCacheDir(serverId)!!.resolve("${id.namespace}/${id.path}/")
        val cacheFile = cacheDir.resolve("$key.cache").toFile()
        if (cacheFile.exists()) cacheFile.delete()
    }


    fun getAsByteArray(key: String): ByteArray? {
        val data = dataMap[key] ?: return null
        val json = Json.encodeToString(serializer, data)
        return json.toByteArray()
    }

    fun fromByteArray(bytes: ByteArray): T {
        val json = String(bytes)
        return Json.decodeFromString(serializer, json)
    }

}