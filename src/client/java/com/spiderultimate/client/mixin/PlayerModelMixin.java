package com.spiderultimate.client.mixin;

import com.spiderultimate.client.render.PoseRender;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses the limbs of the player model for crawling, gliding, web holding and shooting. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
	private void spiderultimate$pose(AvatarRenderState state, CallbackInfo ci) {
		PoseRender.applyLimbs((PlayerModel) (Object) this, state);
	}
}
