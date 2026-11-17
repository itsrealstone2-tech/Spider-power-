package com.spiderultimate.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spiderultimate.client.render.PoseRender;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the player renderer: remembers the pose of the player being drawn and orients the body. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"), require = 0)
	private void spiderultimate$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
		PoseRender.capture(entity, state, partial);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("HEAD"), require = 0)
	private void spiderultimate$rotate(AvatarRenderState state, PoseStack stack, float bodyRot, float scale, CallbackInfo ci) {
		PoseRender.applyRotation(state, stack, bodyRot);
	}
}
