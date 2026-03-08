package com.github.kuripasanda.synclib.config;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.ExcludeFromScreen;
import io.wispforest.owo.config.annotation.RestartRequired;
import io.wispforest.owo.config.annotation.Sync;

@Config(name = "config", wrapperName = "SyncLibServerConfig")
public class SyncLibServerConfigModel {

    /**
     * サーバーID
     * ※クライアントに共有されます。
     */
    @ExcludeFromScreen
    @Sync(Option.SyncMode.INFORM_SERVER)
    public String serverId = "default_server";

    /**
     * 難読化に使用されるキー
     * ※クライアントのコンフィグには共有されませんが、サーバー参加時にクライアントに送信され、メモリ上ではクライアントもこの値を保持することになります。
     */
    @RestartRequired
    @ExcludeFromScreen
    @Sync(Option.SyncMode.NONE)
    public String obfuscateKey = "default_obfuscation_key";

}
