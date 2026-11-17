package com.spiderultimate.client;

import com.spiderultimate.common.Ability;
import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import com.spiderultimate.physics.V3;
import com.spiderultimate.server.PlayerData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Special abilities (selected with B, used with V). The server validates cost and cooldown again. */
public final class Abilities {
	private Abilities() {
	}

	private static void toast(String key) {
		ClientState.toast(Component.translatable(key).getString());
	}

	public static void use(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !p.isAlive() || p.isSpectator()) {
			return;
		}
		SpiderConfig c = SpiderConfig.get();
		if (!SpiderGear.hasGear(p)) {
			toast("spiderultimate.msg.no_gear");
			return;
		}
		Ability a = ClientState.ability;
		SpiderConfig.AbilityCfg cfg = a.cfg(c);
		if (!cfg.enabled) {
			toast("spiderultimate.msg.disabled");
			return;
		}
		int cd = PlayerData.CD_ABILITY + a.ordinal();
		if (ClientState.cooldowns[cd] > 0) {
			toast("spiderultimate.msg.cooldown");
			return;
		}
		boolean free = p.isCreative() && c.creativeInfiniteEnergy;
		if (!free && ClientState.energy < cfg.energyCost) {
			toast("spiderultimate.msg.no_energy");
			return;
		}
		if ((a == Ability.SLOW_MOTION) && !mc.hasSingleplayerServer()) {
			toast("spiderultimate.msg.slow_motion_sp");
			return;
		}

		Vec3 look = p.getLookAngle();
		Vec3 aim = Vec3.ZERO;
		boolean ok;
		switch (a) {
			case SWING_BOOST -> ok = MotionController.boost(mc, 1.3, Math.max(c.maxSwingSpeed * 1.3, 1.0));
			case QUICK_ZIP -> {
				V3 pt = WebShooter.quickZip(mc);
				ok = pt != null;
				if (ok) {
					aim = ClientUtil.vec(pt);
				}
			}
			case AIR_TRICK -> {
				ok = MotionController.airborneControlled() || !p.onGround();
				if (ok) {
					MotionController.startTrick(14);
				}
			}
			case WEB_SHIELD -> {
				ClientState.cooldowns[PlayerData.CD_SHIELD] = c.shieldDurationTicks;
				ok = true;
			}
			case WALL_LAUNCH -> ok = MotionController.wallLaunch(mc, 1.5);
			default -> ok = true;
		}
		if (!ok) {
			toast("spiderultimate.msg.not_now");
			return;
		}
		if (!free) {
			ClientState.energy -= (float) cfg.energyCost;
		}
		ClientState.cooldowns[cd] = cfg.cooldownTicks;
		if (ClientState.serverHasMod) {
			ClientPlayNetworking.send(new ModPackets.AbilityUse(a.ordinal(), look.x, look.y, look.z, aim.x, aim.y, aim.z));
		}
	}
}
