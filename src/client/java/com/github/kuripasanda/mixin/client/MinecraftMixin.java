package com.github.kuripasanda.mixin.client;


import com.github.kuripasanda.synclib.client.event.ClientPlayerEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@Shadow public HitResult hitResult;

	@Shadow @Nullable public LocalPlayer player;

	@Shadow @Nullable public ClientLevel level;

	@Unique
	private boolean mixinLeaveDispatched;

	@Inject( method = "startUseItem", at = @At("HEAD"), cancellable = true)
	public void startUseItem(CallbackInfo ci) {
		if (player == null) return;
		if (player.isHandsBusy()) return;

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack item = player.getItemInHand(hand);

			InteractionResult result = ClientPlayerEvents.INTERACT.invoker().onInteract(Minecraft.getInstance(), item, hand, hitResult);
			if (result != InteractionResult.PASS) {
				ci.cancel();
				return;
			}
		}

	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void onSetLevel(@Nullable ClientLevel newLevel, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
		ClientLevel oldLevel = this.level;

		if (oldLevel == null && newLevel != null) {
			mixinLeaveDispatched = false;
			ClientPlayerEvents.SERVER_JOIN.invoker().onServerJoin(Minecraft.getInstance(), newLevel);
			return;
		}

		if (oldLevel != null && newLevel == null) {
			fireServerLeave(oldLevel);
			return;
		}

		if (oldLevel == null) return;
		if (oldLevel.dimension().equals(newLevel.dimension())) return;

		mixinLeaveDispatched = false;
		ClientPlayerEvents.DIMENSION_CHANGE.invoker().onDimensionChange(Minecraft.getInstance(), oldLevel, newLevel);
	}

	@Inject(method = "clearClientLevel", at = @At("HEAD"), require = 0)
	private void onClearClientLevel(@Nullable Screen screen, CallbackInfo ci) {
		fireServerLeave(this.level);
	}

	@Inject(method = "disconnect()V", at = @At("HEAD"), require = 0)
	private void onDisconnect(CallbackInfo ci) {
		fireServerLeave(this.level);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), require = 0)
	private void onDisconnectWithScreen(@Nullable Screen screen, CallbackInfo ci) {
		fireServerLeave(this.level);
	}

	@Unique
	private void fireServerLeave(@Nullable ClientLevel leftLevel) {
		if (leftLevel == null || mixinLeaveDispatched) return;
		mixinLeaveDispatched = true;
		ClientPlayerEvents.SERVER_LEAVE.invoker().onServerLeave(Minecraft.getInstance(), leftLevel);
	}

}
