package com.spiderultimate.client;

import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Spider sense: finds nearby danger, highlights hostile mobs and drives the HUD indicator. */
public final class SpiderSense {
	public record Threat(Vec3 pos, boolean projectile, double distance) {
	}

	public static final List<Threat> threats = new ArrayList<>();
	/** Ticks of the warning flash that are left. */
	public static int flash;
	private static final Set<Integer> glowing = new HashSet<>();
	private static int pingCooldown;

	private SpiderSense() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		threats.clear();
		if (flash > 0) {
			flash--;
		}
		if (pingCooldown > 0) {
			pingCooldown--;
		}
		SpiderConfig c = SpiderConfig.get();
		if (p == null || mc.level == null || !c.spiderSense || !ClientState.senseOn || !SpiderGear.hasGear(p) || !p.isAlive()) {
			clearGlow(mc);
			return;
		}
		Set<Integer> now = new HashSet<>();
		double near = Double.MAX_VALUE;
		AABB box = p.getBoundingBox().inflate(c.senseRange);
		for (Entity e : mc.level.getEntities(p, box, ent -> ent.isAlive())) {
			Vec3 center = e.getBoundingBox().getCenter();
			double d = center.distanceTo(p.position());
			if (c.senseHostiles && e instanceof Enemy && e instanceof LivingEntity && d <= c.senseRange) {
				threats.add(new Threat(center, false, d));
				near = Math.min(near, d);
				if (c.senseGlow) {
					e.setGlowingTag(true);
					now.add(e.getId());
				}
			} else if (c.senseProjectiles && e instanceof Projectile pr && pr.getOwner() != p && d < 16.0) {
				Vec3 toMe = p.getBoundingBox().getCenter().subtract(center);
				if (pr.getDeltaMovement().dot(toMe) > 0.0 && pr.getDeltaMovement().lengthSqr() > 0.01) {
					threats.add(new Threat(center, true, d));
					near = Math.min(near, d);
				}
			}
		}
		for (int id : new ArrayList<>(glowing)) {
			if (!now.contains(id)) {
				Entity e = mc.level.getEntity(id);
				if (e != null) {
					e.setGlowingTag(false);
				}
			}
		}
		glowing.clear();
		glowing.addAll(now);
		if (near < 9.0 && pingCooldown <= 0) {
			pingCooldown = 50;
			flash = 14;
			mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.SPIDER_SENSE, SoundSource.PLAYERS, 0.6F, 1.0F, false);
		}
	}

	private static void clearGlow(Minecraft mc) {
		if (mc.level != null) {
			for (int id : glowing) {
				Entity e = mc.level.getEntity(id);
				if (e != null) {
					e.setGlowingTag(false);
				}
			}
		}
		glowing.clear();
	}

	public static void reset() {
		glowing.clear();
		threats.clear();
		flash = 0;
	}
}
