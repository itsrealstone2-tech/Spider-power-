package com.spiderultimate.common;

import com.spiderultimate.config.SpiderConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Super jump and enhanced speed are applied as attribute modifiers (fall protection is an ALLOW_DAMAGE hook), so vanilla movement
 * code (which is predicted on the client) uses them naturally. Runs on both sides; the server is authoritative.
 */
public final class PowerAttributes {
	private static final Identifier JUMP_ID = Identifier.fromNamespaceAndPath("spiderultimate", "power_jump");
	private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("spiderultimate", "power_speed");
	/** Vanilla base jump strength. */
	private static final double BASE_JUMP = 0.42;
	private static final double SUPER_JUMP_VELOCITY = 0.95;

	private static final Map<UUID, double[]> SERVER_STATE = new HashMap<>();
	private static final Map<UUID, double[]> CLIENT_STATE = new HashMap<>();

	private PowerAttributes() {
	}

	public static void clear() {
		SERVER_STATE.clear();
		CLIENT_STATE.clear();
	}

	public static void forget(Player p) {
		SERVER_STATE.remove(p.getUUID());
		CLIENT_STATE.remove(p.getUUID());
	}

	public static void update(Player p, boolean active) {
		SpiderConfig c = SpiderConfig.get();
		double jump = 0;
		double speed = 0;
		if (active) {
			if (c.superJump && c.jumpStrength > 0.01) {
				jump = SUPER_JUMP_VELOCITY * c.jumpStrength / BASE_JUMP - 1.0;
			}
			if (c.superSpeed > 0.001) {
				speed = c.superSpeed * (p.isSprinting() ? 0.55 : 0.20);
			}
		}
		Map<UUID, double[]> map = p.level().isClientSide() ? CLIENT_STATE : SERVER_STATE;
		double[] last = map.computeIfAbsent(p.getUUID(), k -> new double[] {Double.NaN, Double.NaN, -1});
		if (last[2] != p.getId()) {
			// A new player object (respawn / dimension change): the attribute instances are fresh again.
			last[0] = Double.NaN;
			last[1] = Double.NaN;
			last[2] = p.getId();
		}
		if (last[0] != jump) {
			apply(p, Attributes.JUMP_STRENGTH, JUMP_ID, jump, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			last[0] = jump;
		}
		if (last[1] != speed) {
			apply(p, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			last[1] = speed;
		}
	}

	private static void apply(Player p, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = p.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		if (amount == 0.0) {
			inst.removeModifier(id);
		} else {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
