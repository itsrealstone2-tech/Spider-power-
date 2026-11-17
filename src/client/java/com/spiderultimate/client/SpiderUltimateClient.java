package com.spiderultimate.client;

import com.spiderultimate.client.render.SpiderRenderer;
import com.spiderultimate.common.Ability;
import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.common.WebMode;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Client entrypoint: keys, tick hooks, networking receivers, HUD and world rendering. */
public class SpiderUltimateClient implements ClientModInitializer {
	private int serverCheckTimer;
	private boolean swingKeyHeld;

	@Override
	public void onInitializeClient() {
		ModKeys.init();
		com.spiderultimate.common.Hooks.webShooterUse = (player, hand) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && player == mc.player) {
				WebShooter.fire(mc, ClientState.mode, WebShooter.leftForHand(mc.player, hand), false);
			}
		};
		registerReceivers();
		SpiderRenderer.init();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("spiderultimate", "hud"), SpiderHud::render);

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			handleKeys(client);
			MotionController.onStartTick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MotionController.onEndTick(client);
			WebShooter.tick(client);
			SpiderSense.tick(client);
			tickRemotes(client);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			MotionController.reset();
			SpiderSense.reset();
			ClientState.serverHasMod = ClientPlayNetworking.canSend(ModPackets.WebFire.TYPE);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MotionController.reset();
			SpiderSense.reset();
			ClientState.serverHasMod = false;
		});
	}

	private void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.Stats.TYPE, (payload, ctx) -> {
			ClientState.energy = payload.energy();
			ClientState.maxEnergy = payload.maxEnergy();
			int[] cd = payload.cooldowns();
			for (int i = 0; i < ClientState.cooldowns.length && i < cd.length; i++) {
				ClientState.cooldowns[i] = cd[i];
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.Deny.TYPE,
			(payload, ctx) -> MotionController.onDeny(ctx.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.WebShot.TYPE,
			(payload, ctx) -> WebShooter.onWebShot(ctx.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.PoseBroadcast.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.player != null && payload.entityId() == mc.player.getId()) {
				return;
			}
			ClientState.Remote r = ClientState.remote(payload.entityId());
			r.pose = payload.pose();
			r.lastUpdate = r.age;
		});
	}

	private void tickRemotes(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (--serverCheckTimer <= 0) {
			serverCheckTimer = 20;
			ClientState.serverHasMod = ClientPlayNetworking.canSend(ModPackets.WebFire.TYPE);
			ClientState.remote.entrySet().removeIf(e -> mc.level.getEntity(e.getKey()) == null);
		}
		ClientState.tickRemote(ClientState.remote(mc.player.getId()), true);
		for (var e : ClientState.remote.entrySet()) {
			if (e.getKey() != mc.player.getId()) {
				ClientState.tickRemote(e.getValue(), false);
			}
			net.minecraft.world.entity.Entity ent = mc.level.getEntity(e.getKey());
			ClientState.Remote r = e.getValue();
			if (ent != null) {
				if (r.hasLast) {
					double d = Math.sqrt((ent.getX() - r.lastX) * (ent.getX() - r.lastX)
						+ (ent.getY() - r.lastY) * (ent.getY() - r.lastY) + (ent.getZ() - r.lastZ) * (ent.getZ() - r.lastZ));
					r.speed += (d - r.speed) * 0.4;
				}
				r.lastX = ent.getX();
				r.lastY = ent.getY();
				r.lastZ = ent.getZ();
				r.hasLast = true;
			}
		}
	}

	private void handleKeys(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		SpiderConfig c = SpiderConfig.get();

		while (ModKeys.RELOAD_CONFIG.consumeClick()) {
			SpiderConfig.load();
			ClientState.toast(Component.translatable("spiderultimate.msg.config_reloaded").getString());
		}
		while (ModKeys.HUD.consumeClick()) {
			c.hudVisible = !c.hudVisible;
			SpiderConfig.save();
		}
		if (mc.screen != null || !SpiderGear.hasGear(p)) {
			// drain the clicks so they do not fire later
			drain();
			swingKeyHeld = false;
			return;
		}

		while (ModKeys.MODE.consumeClick()) {
			ClientState.mode = ClientState.mode.next();
			ClientState.toast(Component.translatable("spiderultimate.hud.mode").getString() + ": "
				+ Component.translatable(ClientState.mode.langKey()).getString());
		}
		while (ModKeys.ABILITY_NEXT.consumeClick()) {
			ClientState.ability = ClientState.ability.next();
			ClientState.toast(Component.translatable("spiderultimate.hud.ability").getString() + ": "
				+ Component.translatable(ClientState.ability.langKey()).getString());
		}
		while (ModKeys.SENSE.consumeClick()) {
			ClientState.senseOn = !ClientState.senseOn;
		}
		while (ModKeys.CRAWL.consumeClick()) {
			ClientState.crawlMode = !ClientState.crawlMode;
			if (!ClientState.crawlMode) {
				MotionController.detach(mc);
			}
			ClientState.toast(Component.translatable(ClientState.crawlMode
				? "spiderultimate.msg.crawl_on" : "spiderultimate.msg.crawl_off").getString());
		}
		while (ModKeys.WINGS.consumeClick()) {
			if (c.webWings) {
				ClientState.wingsOn = !ClientState.wingsOn;
				ClientState.toast(Component.translatable(ClientState.wingsOn
					? "spiderultimate.msg.wings_on" : "spiderultimate.msg.wings_off").getString());
			}
		}
		while (ModKeys.SPECIAL.consumeClick()) {
			Abilities.use(mc);
		}
		while (ModKeys.RELEASE.consumeClick()) {
			if (MotionController.hasRope()) {
				MotionController.release(mc, 3, false, true);
			}
		}

		// shooting
		while (ModKeys.SHOOT_LEFT.consumeClick()) {
			WebShooter.fire(mc, ClientState.mode, Boolean.TRUE, false);
		}
		while (ModKeys.SHOOT_RIGHT.consumeClick()) {
			WebShooter.fire(mc, ClientState.mode, Boolean.FALSE, false);
		}
		while (ModKeys.SHOOT.consumeClick()) {
			WebShooter.fire(mc, ClientState.mode, null, false);
		}
		if (ClientState.mode == WebMode.RAPID && ModKeys.SHOOT.isDown()) {
			WebShooter.fire(mc, WebMode.RAPID, null, false);
		}

		// swing key: toggle (default) or hold
		boolean down = ModKeys.SWING.isDown();
		boolean pressed = false;
		while (ModKeys.SWING.consumeClick()) {
			pressed = true;
		}
		if (c.swingToggle) {
			if (pressed) {
				if (MotionController.hasRope()) {
					MotionController.release(mc, 3, false, true);
				} else {
					WebShooter.fire(mc, WebMode.SINGLE, null, true);
				}
			}
		} else {
			if (pressed && !MotionController.hasRope()) {
				WebShooter.fire(mc, WebMode.SINGLE, null, true);
			}
			if (swingKeyHeld && !down && MotionController.hasRope()) {
				MotionController.release(mc, 3, false, true);
			}
		}
		swingKeyHeld = down;
	}

	private void drain() {
		while (ModKeys.SHOOT.consumeClick()) { }
		while (ModKeys.SWING.consumeClick()) { }
		while (ModKeys.SENSE.consumeClick()) { }
		while (ModKeys.WINGS.consumeClick()) { }
		while (ModKeys.MODE.consumeClick()) { }
		while (ModKeys.SPECIAL.consumeClick()) { }
		while (ModKeys.ABILITY_NEXT.consumeClick()) { }
		while (ModKeys.CRAWL.consumeClick()) { }
		while (ModKeys.RELEASE.consumeClick()) { }
		while (ModKeys.SHOOT_LEFT.consumeClick()) { }
		while (ModKeys.SHOOT_RIGHT.consumeClick()) { }
	}
}
