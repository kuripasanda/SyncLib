package com.github.kuripasanda.mixin.client;

import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {

    @Accessor
    ChannelFuture getChannelFuture();

    @Accessor
    Connection getConnection();

}
