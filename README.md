# SyncLib
サーバー・クライアント間のデータ同期を手軽に行えるようにする簡易的なライブラリです。  
※個人用・所属するプロジェクト用に開発したものですので、バグ等が発生したとしても自己責任でお願いします。  

## 依存関係

### 1. 前提MOD
このライブラリを使用する際は、サーバー・クライアント両方に以下のMODを導入してください。
| MOD   | 備考  |
| ----- | ----- |
| owo-lib | SyncLibのコンフィグ生成に使用しています |
| Fabric Kotlin Language | FabricModをKotlinで作成するために必須の前提MODです |

### 2. build.gradle
自身のプロジェクトの `build.gradle`に [Kotlin Seriaization](https://github.com/Kotlin/kotlinx.serialization/tree/master?tab=readme-ov-file#gradle)を依存関係として追加する必要があります。
```groovy
plugins {
    id 'org.jetbrains.kotlin.multiplatform' version '2.3.0'
    id 'org.jetbrains.kotlin.plugin.serialization' version '2.3.0'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0"
}
```
  

  
## 使用方法
SyncLibを使用するには、各データ専用の**レジストリ**を作成し、そこに `@Serializable`アノテーションが適用されたデータ用のクラスを格納する必要があります。  

### 1. データ用のクラスを作成
データはパケットで送信する前に[KotlinxSerialization](https://github.com/Kotlin/kotlinx.serialization)でJSONにパースしてから送信されます。  
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ExampleData(
    val id: Int,
    val message: String,
)
```

### 2. レジストリの作成
ヘルパー関数を使用することで、新規作成後、登録されたレジストリのインスタンスを取得できます。
```kotlin
// レジストリを登録
val exampleDataRegistry = SyncHelper.createRegistry(
  id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "example"), // レジストリのID
  serializer = ExampleData.serializer(), // 同期対象のデータのシリアライザ
  obfuscatedClientSide = false, // クライアント側でキャッシュする際に難読化するかどうか
  onRegister = { newData -> newData }, // データがレジストリに登録される前に呼び出されるコールバック関数。ここで登録されるデータを編集できます。
  onUnregister = { data -> } // データがレジストリから削除されたときに呼び出されるコールバック関数。
)
```

### 3. データの登録
レジストリにデータを登録します。
```kotlin
exampleDataRegistry.register(
  "key", // レジストリ内の要素のキー
  ExampleData(id = 1, message = "Hello, world!") // 登録するデータ
)
```
