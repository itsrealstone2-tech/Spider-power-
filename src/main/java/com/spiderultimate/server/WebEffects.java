package com.spiderultimate.server;

import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server side effects of the non-rope web modes (rapid, pull, trap, bomb, multi target) and of landings. */
public final class WebEffects {
	private static final int MAX_TEMP_WEBS = 3000;

	private record TempWeb(ResourceKey<Level> dimension, BlockPos pos, int expireTick) {
	}

	private static final List<TempWeb> TEMP_WEBS = new ArrayList<>();

	private WebEffects() {
	}

	// ------------------------------------------------------------------ temporary cobwebs

	/** Places a cobweb in an empty block; it disappears after {@code durationTicks}. */
	public static boolean placeTempWeb(ServerLevel level, BlockPos pos, int durationTicks) {
		if (TEMP_WEBS.size() >= MAX_TEMP_WEBS || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		if (!level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL)) {
			return false;
		}
		TEMP_WEBS.add(new TempWeb(level.dimension(), pos.immutable(), level.getServer().getTickCount() + durationTicks));
		return true;
	}

	/** Removes expired cobwebs; every {@code force} removes all of them (server shutdown). */
	public static void tickTempWebs(MinecraftServer server, boolean force) {
		if (TEMP_WEBS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<TempWeb> it = TEMP_WEBS.iterator();
		while (it.hasNext()) {
			TempWeb w = it.next();
			if (!force && now < w.expireTick) {
				continue;
			}
			ServerLevel level = server.getLevel(w.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			if (!level.hasChunkAt(w.pos)) {
				if (force || now > w.expireTick + 6000) {
					it.remove();
				}
				continue;
			}
			if (level.getBlockState(w.pos).is(Blocks.COBWEB)) {
				level.removeBlock(w.pos, false);
			}
			it.remove();
		}
	}

	// ------------------------------------------------------------------ entangling

	public static void entangle(LivingEntity e, int ticks, int amplifier) {
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier, false, true, true));
	}

	/** Puts cobweb at the feet (and head) of the entity and slows it down. */
	public static void trapEntity(ServerLevel level, LivingEntity e, int durationTicks) {
		entangle(e, durationTicks, 6);
		BlockPos feet = e.blockPosition();
		placeTempWeb(level, feet, durationTicks);
		if (e.getBbHeight() > 1.4) {
			placeTempWeb(level, feet.above(), durationTicks);
		}
		burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 14, 0.5);
	}

	/** Webs a spot: the first empty block on the hit surface. */
	public static void trapSpot(ServerLevel level, Vec3 hit, int durationTicks) {
		BlockPos pos = BlockPos.containing(hit);
		if (!placeTempWeb(level, pos, durationTicks)) {
			placeTempWeb(level, pos.above(), durationTicks);
		}
		burst(level, hit, 10, 0.4);
	}

	/** Web grenade: entangles everything alive near the impact and fills the air with cobweb. */
	public static void bomb(ServerLevel level, ServerPlayer shooter, Vec3 center, SpiderConfig c) {
		double r = c.bombRadius;
		AABB box = new AABB(center, center).inflate(r);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, le -> le != shooter && le.isAlive())) {
			if (e.position().distanceTo(center) <= r + 0.8) {
				entangle(e, c.webDurationTicks, 6);
			}
		}
		int placed = 0;
		int ir = (int) Math.ceil(r);
		BlockPos origin = BlockPos.containing(center);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -ir; dx <= ir && placed < 48; dx++) {
			for (int dy = -ir; dy <= ir && placed < 48; dy++) {
				for (int dz = -ir; dz <= ir && placed < 48; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r) {
						continue;
					}
					p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (p.distSqr(shooter.blockPosition()) < 4.0 || p.distSqr(shooter.blockPosition().above()) < 2.0) {
						continue;
					}
					// every second block only, so it looks like a net and not a solid cube
					if (((dx + dy + dz) & 1) == 0 && placeTempWeb(level, p, c.webDurationTicks)) {
						placed++;
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
		burst(level, center, 60, r * 0.4);
		level.playSound(null, center.x, center.y, center.z, ModSounds.WEB_HIT, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	// ------------------------------------------------------------------ pulling

	/** Yanks a mob or item towards the shooter. Returns false if the target is not allowed. */
	public static boolean pull(ServerPlayer shooter, Entity target, SpiderConfig c) {
		if (target instanceof ServerPlayer && !c.pullPlayers) {
			return false;
		}
		if (!(target instanceof LivingEntity) && !(target instanceof ItemEntity)) {
			return false;
		}
		Vec3 to = shooter.position().add(0, 0.4, 0).subtract(target.position());
		double dist = to.length();
		if (dist < 1.0E-3) {
			return false;
		}
		Vec3 dir = to.scale(1.0 / dist);
		double speed = Mth.clamp(dist * 0.11 + 0.3, 0.45, 1.7);
		target.setDeltaMovement(dir.x * speed, dir.y * speed + 0.25, dir.z * speed);
		target.hurtMarked = true;
		target.fallDistance = 0.0;
		if (target.level() instanceof ServerLevel sl) {
			burst(sl, target.position().add(0, target.getBbHeight() * 0.5, 0), 8, 0.3);
		}
		return true;
	}

	/** Rapid fire hit: a little slowness and a splat. */
	public static void rapidHit(ServerLevel level, Entity target, Vec3 hit) {
		if (target instanceof LivingEntity le) {
			entangle(le, 50, 2);
		}
		level.sendParticles(ParticleTypes.WHITE_ASH, hit.x, hit.y, hit.z, 6, 0.12, 0.12, 0.12, 0.02);
		level.playSound(null, hit.x, hit.y, hit.z, ModSounds.WEB_HIT, SoundSource.PLAYERS, 0.5F, 1.5F);
	}

	// ------------------------------------------------------------------ landing and dodging

	/** Web impact landing: a dust ring, a thud and webbed up enemies around the landing spot. */
	public static void landing(ServerLevel level, ServerPlayer p, float fallDamage) {
		SpiderConfig c = SpiderConfig.get();
		Vec3 at = p.position();
		float strength = Mth.clamp(fallDamage / 10.0F, 0.2F, 1.0F);
		int count = 24 + (int) (strength * 40);
		for (int i = 0; i < count; i++) {
			double a = i * (Math.PI * 2.0 / count);
			double sp = 0.15 + strength * 0.2;
			level.sendParticles(ParticleTypes.POOF, at.x + Math.cos(a) * 0.6, at.y + 0.1, at.z + Math.sin(a) * 0.6,
				0, Math.cos(a), 0.05, Math.sin(a), sp);
		}
		level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y + 0.2, at.z, 30, 1.2, 0.2, 1.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_LAND, SoundSource.PLAYERS, 0.7F + strength * 0.6F, 0.9F);
		double radius = 2.5 + strength * 2.0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius), le -> le != p && le.isAlive())) {
			Vec3 away = e.position().subtract(at);
			double d = away.length();
			if (d < 1.0E-3 || d > radius) {
				continue;
			}
			Vec3 dir = away.scale(1.0 / d);
			e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.5 * strength, 0.25 * strength, dir.z * 0.5 * strength));
			e.hurtMarked = true;
			if (!(e instanceof ServerPlayer)) {
				entangle(e, Math.min(c.webDurationTicks, 100), 3);
			}
		}
	}

	public static void dodge(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.4, 0.5, 0.4, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	public static void shieldHit(ServerLevel level, ServerPlayer p) {
		Vec3 at = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 16, 0.5, 0.7, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_SHIELD, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	// ------------------------------------------------------------------ particles

	/** A puff of web: used wherever a web sticks to something. */
	public static void burst(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, Math.max(1, count / 4), spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
	}
}
