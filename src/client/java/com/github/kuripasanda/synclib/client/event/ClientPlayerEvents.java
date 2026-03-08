package com.github.kuripasanda.synclib.client.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

public class ClientPlayerEvents {

    /** プレイヤーがインテラクトした際のイベント。左手と右手で2回呼び出されます。 */
    public static final Event<Interact> INTERACT = EventFactory.createArrayBacked(Interact.class, listeners -> (client, itemStack, hand, hitResult) -> {
        for (Interact listener : listeners) {
            InteractionResult result = listener.onInteract(client, itemStack, hand, hitResult);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    });

    /** クライアントがワールドに参加した際のイベント。 */
    public static final Event<ServerJoin> SERVER_JOIN = EventFactory.createArrayBacked(ServerJoin.class, listeners -> (client, joinedLevel) -> {
        for (ServerJoin listener : listeners) {
            listener.onServerJoin(client, joinedLevel);
        }
    });

    /** クライアントがワールドから脱退した際のイベント。 */
    public static final Event<ServerLeave> SERVER_LEAVE = EventFactory.createArrayBacked(ServerLeave.class, listeners -> (client, leftLevel) -> {
        for (ServerLeave listener : listeners) {
            listener.onServerLeave(client, leftLevel);
        }
    });

    /** プレイヤーがクライアント上でディメンション移動した際のイベント。 */
    public static final Event<DimensionChange> DIMENSION_CHANGE = EventFactory.createArrayBacked(DimensionChange.class, listeners -> (client, fromLevel, toLevel) -> {
        for (DimensionChange listener : listeners) {
            listener.onDimensionChange(client, fromLevel, toLevel);
        }
    });

    @FunctionalInterface
    public interface Interact {
        InteractionResult onInteract(Minecraft client, ItemStack mainHandItem, InteractionHand hand, HitResult hitResult);
    }

    @FunctionalInterface
    public interface ServerJoin {
        void onServerJoin(Minecraft client, ClientLevel joinedLevel);
    }

    @FunctionalInterface
    public interface ServerLeave {
        void onServerLeave(Minecraft client, ClientLevel leftLevel);
    }

    @FunctionalInterface
    public interface DimensionChange {
        void onDimensionChange(Minecraft client, ClientLevel fromLevel, ClientLevel toLevel);
    }

}
