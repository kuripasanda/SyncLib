package com.github.kuripasanda.synclib.event;

import com.github.kuripasanda.api.sync.AbstractSyncRegistry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;

import java.util.UUID;

public class ServerSyncLibConfigurationEvent {

    /** 各レジストリがクライアントに同期される前に呼び出されるイベントです。このタイミングでレジストリのデータを変更できます。 */
    public static final Event<BeforeSync> BEFORE_SYNC = EventFactory.createArrayBacked(BeforeSync.class, listeners -> (server, registry, playerUUID) -> {
        for (BeforeSync listener : listeners) {
            InteractionResult result = listener.beforeSync(server, registry, playerUUID);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    });

    @FunctionalInterface
    public interface BeforeSync {
        InteractionResult beforeSync(MinecraftServer server, AbstractSyncRegistry<?> registry, UUID playerUUID);
    }

}
