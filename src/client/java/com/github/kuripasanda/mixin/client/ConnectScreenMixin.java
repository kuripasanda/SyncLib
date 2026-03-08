package com.github.kuripasanda.mixin.client;

import com.github.kuripasanda.SyncLibClient;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {

    @Unique
    private final Font font = Minecraft.getInstance().font;

    @Unique
    private int width = 0;
    @Unique
    private int height = 0;

    @Inject(method = "init", at = @At("HEAD"))
    public void init(CallbackInfo ci) {
        Window window = Minecraft.getInstance().getWindow();
        this.width = window.getGuiScaledWidth();
        this.height = window.getGuiScaledHeight();
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int statusOffsetY = this.height / 2 - 50; // バニラのステータスのY座標
        Component subTitle = SyncLibClient.INSTANCE.getConnectScreenSubStatus();

        if (subTitle != null) {
            guiGraphics.drawCenteredString(this.font, subTitle, this.width / 2, statusOffsetY + 20, CommonColors.GRAY);
        }
    }

}
