package com.github.kuripasanda.api.sync

import com.github.kuripasanda.SyncLib
import com.github.kuripasanda.api.obfuscate.ObfuscatedResourceLocation
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

abstract class AbstractSyncRegistry<T: Any>(
    open val id: ResourceLocation,
    open val serializer: KSerializer<T>,
    open val obfuscatedClientSide: Boolean,
) {

    /**
     * レジストリにデータをキーで登録します。既に同じキーが存在する場合は上書きされます。
     * ※主にSyncLib内部で使用するメソッドです。
     */
    @Environment(EnvType.CLIENT)
    abstract fun registerFromByteArray(key: String, bytes: ByteArray)

    /** レジストリにキャッシュファイルからデータを読み込んで登録します。既に同じキーが存在する場合は上書きされます。
     * キャッシュファイルが存在しない場合や、キャッシュファイルの内容が無効な場合は例外がスローされます。
     * @param serverId サーバーID
     * @param key レジストリ内の要素のキー
     * @param environment 実行している環境
     * @throws NoSuchFileException 指定されたキーのキャッシュファイルが存在しない場合、またはキャッシュファイルが有効なファイルでない場合にスローされます。
     * @throws SerializationException 復号特有の誤りが発生した場合
     * @throws IllegalArgumentException デコード入力が有効なインスタンスでない場合
     */
    @Environment(EnvType.CLIENT)
    abstract fun registerFromCacheFile(serverId: String, key: String, environment: EnvType)

    /** レジストリ内のすべてのデータをクリアします。 */
    abstract fun clear()

    /** データのハッシュ値を計算します。データはJson形式でシリアライズされ、そのバイト列のSHA-256ハッシュが計算されます。 */
    open fun calculateHash(data: T): String {
        val serialized = Json.encodeToString(serializer, data)
        val hashBytes = SyncLib.digest.digest(serialized.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * レジストリのキャッシュディレクトリのパスを返します。キャッシュディレクトリは、サーバーIDとレジストリIDに基づいて構築されます。
     * サーバー環境では通常のレジストリIDを使用し、クライアント環境では難読化が有効であれば難読化されたレジストリIDを使用します。
     */
    @Environment(EnvType.CLIENT)
    open fun getRegistryCacheDir(serverId: String): Path {
        val registryId = getRegistryIdObfuscated(EnvType.CLIENT)
        return SyncHelper.getCacheDir(serverId)!!.resolve("${registryId.namespace}/${registryId.path}")
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
    abstract fun saveCacheToFile(serverId: String, key: String, environment: EnvType)

    protected fun saveCacheToFileInternal(serverId: String, key: String, data: T, environment: EnvType) {
        val cacheDir = getRegistryCacheDir(serverId)
        cacheDir.toFile().mkdirs()

        // 難読化が有効であれば、キャッシュファイルの名前も難読化する
        val finalKey = if (obfuscatedClientSide) SyncLib.obfuscator.obfuscate(key) else key

        val cacheFile = cacheDir.resolve("$finalKey.cache").toFile()
        val jsonPlaneText = Json.encodeToString(serializer, data)
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
    open fun loadCacheFromFile(serverId: String, key: String, environment: EnvType): T {
        val cacheDir = getRegistryCacheDir(serverId)
        val finalKey = if (obfuscatedClientSide) SyncLib.obfuscator.obfuscate(key) else key
        val cacheFile = cacheDir.resolve("$finalKey.cache").toFile()
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
    @Environment(EnvType.CLIENT)
    @Throws(NoSuchElementException::class, IOException::class)
    open fun deleteCacheFile(serverId: String, key: String) {
        val cacheDir = SyncHelper.getCacheDir(serverId)!!.resolve("${id.namespace}/${id.path}/")
        val cacheFile = cacheDir.resolve("$key.cache").toFile()
        if (cacheFile.exists()) cacheFile.delete()
    }

    /**
     * バイト配列からデータをデシリアライズして返します。バイト配列はJson形式の文字列をバイト配列に変換したものになっている必要があります。
     * @throws SerializationException デコード特有の誤りが発生した場合
     * @throws IllegalArgumentException デコード入力が有効なインスタンスでない場合
     */
    @Throws(SerializationException::class, IllegalArgumentException::class)
    open fun fromByteArray(bytes: ByteArray): T {
        val json = String(bytes)
        return Json.decodeFromString(serializer, json)
    }

    /**
     * レジストリのIDを環境に応じて返します。
     * サーバー環境では通常の識別子を返し、クライアント環境では難読化が有効であれば難読化された識別子を返します。
     */
    open fun getRegistryIdObfuscated(environment: EnvType): ObfuscatedResourceLocation {
        return when (environment) {
            EnvType.SERVER -> ObfuscatedResourceLocation.fromResourceLocation(id)
            EnvType.CLIENT -> {
                val namespace = if (obfuscatedClientSide) SyncLib.obfuscator.obfuscate(id.namespace) else id.namespace
                val path = if (obfuscatedClientSide) SyncLib.obfuscator.obfuscate(id.path) else id.path
                ObfuscatedResourceLocation(namespace, path)
            }
        }
    }

    /**
     * プレイヤーにレジストリの要素を同期します。
     * @param player 同期対象のプレイヤーを指定します。
     * @param key レジストリ内の要素のキーを指定します。
     * @throws NoSuchElementException 指定されたキーがレジストリ内に存在しない場合にスローされます。
     */
    @Environment(EnvType.SERVER)
    @Throws(NoSuchElementException::class)
    abstract fun syncToPlayer(player: ServerPlayer, key: String)

}