package com.spiderultimate.server;

import com.spiderultimate.common.Ability;
import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.common.PowerAttributes;
import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.common.WebMode;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import com.spiderultimate.physics.V3;
import com.spiderultimate.registry.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority: validates every request of the clients (gear, energy, cooldowns, range, line of sight),
 * keeps the validated rope anchors, applies the web effects on the world and relays the movement state to
 * the players that can see the shooter. Movement itself is simulated by the owning client (vanilla design),
 * the server checks its inputs and limits and releases anything that does not make sense.
 */
public final class SpiderServer {
	private static final Map<UUID, PlayerData> DATA = new HashMap<>();
	private static long slowMotionEndNanos;
	private static boolean slowMotionActive;

	private SpiderServer() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.WebFire.TYPE, (payload, ctx) -> onWebFire(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.Release.TYPE, (payload, ctx) -> onRelease(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.AbilityUse.TYPE, (payload, ctx) -> onAbility(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.PoseUpdate.TYPE, (payload, ctx) -> onPose(ctx.player(), payload.pose()));

		ServerTickEvents.END_SERVER_TICK.register(SpiderServer::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderServer::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			DATA.remove(p.getUUID());
			PowerAttributes.forget(p);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			endSlowMotion(server);
			WebEffects.tickTempWebs(server, true);
			DATA.clear();
			PowerAttributes.clear();
		});
	}

	public static PlayerData data(ServerPlayer p) {
		PlayerData d = DATA.computeIfAbsent(p.getUUID(), k -> new PlayerData());
		if (!d.initialised) {
			d.initialised = true;
			d.energy = SpiderConfig.get().maxEnergy;
		}
		return d;
	}

	// ====================================================================== helpers

	private static boolean freeEnergy(ServerPlayer p) {
		return p.isCreative() && SpiderConfig.get().creativeInfiniteEnergy;
	}

	private static void send(ServerPlayer p, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(p, payload.type())) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private static void sendToWatchers(ServerPlayer p, CustomPacketPayload payload, boolean includeSelf) {
		for (ServerPlayer o : PlayerLookup.tracking(p)) {
			send(o, payload);
		}
		if (includeSelf) {
			send(p, payload);
		}
	}

	private static void deny(ServerPlayer p, int reason, int slotMask) {
		send(p, new ModPackets.Deny(reason, slotMask));
	}

	private static boolean finite(double... v) {
		for (double d : v) {
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				return false;
			}
		}
		return true;
	}

	/** Sends the (sanitised) movement state of the player to everybody who can see them. */
	private static void relayPose(ServerPlayer p, PlayerData d) {
		PoseData out = d.pose.copy();
		out.anchor[0] = d.anchors[0];
		out.anchor[1] = d.anchors[1];
		if (d.cooldowns[PlayerData.CD_SHIELD] > 0) {
			out.flags |= MotionMode.FLAG_SHIELD;
		} else {
			out.flags &= ~MotionMode.FLAG_SHIELD;
		}
		boolean active = out.active();
		if (!active && !d.lastPoseActive) {
			return;
		}
		d.lastPoseActive = active;
		sendToWatchers(p, new ModPackets.PoseBroadcast(p.getId(), out), false);
	}

	/** Checks that the point the client claims to have hit is really the first surface along its ray. */
	private static BlockHitResult validateBlockTarget(ServerPlayer p, ServerLevel level, Vec3 origin, Vec3 target, SpiderConfig c) {
		Vec3 eye = p.getEyePosition();
		Vec3 from = origin.distanceTo(eye) <= 3.5 ? origin : eye;
		double dist = target.distanceTo(from);
		if (dist < 0.3 || target.distanceTo(eye) > c.webRange + c.serverRangeSlack) {
			return null;
		}
		Vec3 dir = target.subtract(from).scale(1.0 / dist);
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(dist + 1.0)),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(target) > 1.25) {
			return null;
		}
		return hit;
	}

	// ====================================================================== web shooter

	private static void onWebFire(ServerPlayer p, ModPackets.WebFire f) {
		SpiderConfig c = SpiderConfig.get();
		PlayerData d = data(p);
		WebMode mode = WebMode.byId(f.mode());
		int slot = f.slot() & 1;
		int ropeMask = mode.isRopeMode() ? (1 << slot) : 0;
		if (!p.isAlive() || p.isSpectator() || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		if (!finite(f.ox(), f.oy(), f.oz(), f.tx(), f.ty(), f.tz())) {
			return;
		}
		if (!SpiderGear.hasGear(p)) {
			deny(p, ModPackets.Deny.NO_GEAR, ropeMask);
			return;
		}
		if (!c.midairShooting && !p.onGround() && !d.hasAnchor()) {
			deny(p, ModPackets.Deny.DISABLED, ropeMask);
			return;
		}
		int hand = f.left() ? PlayerData.CD_LEFT : PlayerData.CD_RIGHT;
		if (d.cooldowns[hand] > 2) {
			deny(p, ModPackets.Deny.COOLDOWN, ropeMask);
			return;
		}
		double cost = mode == WebMode.DUAL ? mode.cost(c) * 0.5 : mode.cost(c);
		if (!freeEnergy(p) && d.energy < cost) {
			deny(p, ModPackets.Deny.NO_ENERGY, ropeMask);
			return;
		}

		Vec3 origin = new Vec3(f.ox(), f.oy(), f.oz());
		Vec3 claimed = new Vec3(f.tx(), f.ty(), f.tz());
		Entity entity = f.entity() >= 0 ? level.getEntity(f.entity()) : null;
		Vec3 hitPoint = claimed;
		BlockHitResult blockHit = null;

		if (entity != null) {
			double range = c.webRange + c.serverRangeSlack;
			boolean ok = entity != p && entity.isAlive()
				&& entity.getBoundingBox().inflate(1.5).contains(claimed)
				&& entity.position().distanceTo(p.getEyePosition()) <= range
				&& (!(entity instanceof LivingEntity) || p.hasLineOfSight(entity));
			if (!ok) {
				deny(p, ModPackets.Deny.INVALID_TARGET, ropeMask);
				return;
			}
			hitPoint = entity.getBoundingBox().getCenter();
		} else {
			blockHit = validateBlockTarget(p, level, origin, claimed, c);
			if (blockHit == null && mode != WebMode.RAPID) {
				deny(p, ModPackets.Deny.INVALID_TARGET, ropeMask);
				return;
			}
			if (blockHit != null) {
				hitPoint = blockHit.getLocation();
			}
		}

		switch (mode) {
			case SINGLE, DUAL, ZIP -> {
				if (blockHit == null) {
					deny(p, ModPackets.Deny.INVALID_TARGET, ropeMask);
					return;
				}
				int use = mode == WebMode.ZIP ? 0 : slot;
				d.anchors[use] = new V3(hitPoint.x, hitPoint.y, hitPoint.z);
				d.anchorBlocks[use] = blockHit.getBlockPos();
				if (mode == WebMode.ZIP) {
					d.clearAnchor(1);
				}
			}
			case RAPID -> {
				if (entity != null || blockHit != null) {
					WebEffects.rapidHit(level, entity, hitPoint);
				}
			}
			case PULL -> {
				if (entity == null || !WebEffects.pull(p, entity, c)) {
					deny(p, ModPackets.Deny.INVALID_TARGET, 0);
					return;
				}
			}
			case TRAP -> {
				if (entity instanceof LivingEntity le) {
					WebEffects.trapEntity(level, le, c.webDurationTicks);
				} else if (blockHit != null) {
					WebEffects.trapSpot(level, Vec3.atCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection())), c.webDurationTicks);
				} else {
					deny(p, ModPackets.Deny.INVALID_TARGET, 0);
					return;
				}
			}
			case BOMB -> {
				Vec3 center = blockHit != null
					? hitPoint.add(blockHit.getDirection().getStepX() * 0.6, blockHit.getDirection().getStepY() * 0.6,
						blockHit.getDirection().getStepZ() * 0.6)
					: hitPoint;
				WebEffects.bomb(level, p, center, c);
			}
		}

		if (!freeEnergy(p)) {
			d.energy -= cost;
		}
		d.cooldowns[hand] = mode == WebMode.DUAL ? c.webCooldownTicks : mode.cooldown(c);
		level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.WEB_SHOOT, SoundSource.PLAYERS, 0.8F, 0.95F + p.getRandom().nextFloat() * 0.15F);
		sendToWatchers(p, new ModPackets.WebShot(p.getId(), f.left(), mode.ordinal(), hitPoint.x, hitPoint.y, hitPoint.z), true);
		if (mode.isRopeMode()) {
			relayPose(p, d);
		}
		sendStats(p, d, true);
	}

	private static void onRelease(ServerPlayer p, ModPackets.Release r) {
		PlayerData d = data(p);
		for (int i = 0; i < 2; i++) {
			if ((r.slotMask() & (1 << i)) != 0) {
				d.clearAnchor(i);
			}
		}
		relayPose(p, d);
	}

	// ====================================================================== abilities

	private static void onAbility(ServerPlayer p, ModPackets.AbilityUse a) {
		SpiderConfig c = SpiderConfig.get();
		PlayerData d = data(p);
		Ability ab = Ability.byId(a.ability());
		SpiderConfig.AbilityCfg cfg = ab.cfg(c);
		if (!p.isAlive() || p.isSpectator() || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		if (!finite(a.dx(), a.dy(), a.dz(), a.ax(), a.ay(), a.az())) {
			return;
		}
		if (!cfg.enabled) {
			deny(p, ModPackets.Deny.DISABLED, 0);
			return;
		}
		if (!SpiderGear.hasGear(p)) {
			deny(p, ModPackets.Deny.NO_GEAR, 0);
			return;
		}
		int cd = PlayerData.CD_ABILITY + ab.ordinal();
		if (d.cooldowns[cd] > 2) {
			deny(p, ModPackets.Deny.COOLDOWN, 0);
			return;
		}
		if (!freeEnergy(p) && d.energy < cfg.energyCost) {
			deny(p, ModPackets.Deny.NO_ENERGY, 0);
			return;
		}

		Vec3 at = p.position().add(0, 1.0, 0);
		switch (ab) {
			case WEB_SHIELD -> {
				d.cooldowns[PlayerData.CD_SHIELD] = c.shieldDurationTicks;
				d.shieldHealth = c.shieldStrength;
				level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_SHIELD, SoundSource.PLAYERS, 1.0F, 1.0F);
				WebEffects.burst(level, at, 30, 0.8);
			}
			case SLOW_MOTION -> {
				MinecraftServer server = level.getServer();
				if (!server.isSingleplayer()) {
					deny(p, ModPackets.Deny.DISABLED, 0);
					return;
				}
				server.tickRateManager().setTickRate((float) c.slowMotionTickRate);
				slowMotionActive = true;
				slowMotionEndNanos = System.nanoTime() + (long) (c.slowMotionSeconds * 1.0E9);
			}
			case QUICK_ZIP -> {
				Vec3 target = new Vec3(a.ax(), a.ay(), a.az());
				BlockHitResult hit = validateBlockTarget(p, level, p.getEyePosition(), target, c);
				if (hit == null) {
					deny(p, ModPackets.Deny.INVALID_TARGET, 1);
					return;
				}
				d.anchors[0] = new V3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
				d.anchorBlocks[0] = hit.getBlockPos();
				d.clearAnchor(1);
				level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_SHOOT, SoundSource.PLAYERS, 0.9F, 1.2F);
				sendToWatchers(p, new ModPackets.WebShot(p.getId(), false, WebMode.ZIP.ordinal(),
					hit.getLocation().x, hit.getLocation().y, hit.getLocation().z), true);
				relayPose(p, d);
			}
			case MULTI_WEB -> {
				if (!multiWeb(p, level, c, new Vec3(a.dx(), a.dy(), a.dz()))) {
					deny(p, ModPackets.Deny.INVALID_TARGET, 0);
					return;
				}
			}
			case SWING_BOOST -> level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_SWING, SoundSource.PLAYERS, 0.8F, 1.3F);
			case AIR_TRICK -> level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_SWING, SoundSource.PLAYERS, 0.5F, 1.7F);
			case WALL_LAUNCH -> {
				level.playSound(null, at.x, at.y, at.z, ModSounds.WEB_LAND, SoundSource.PLAYERS, 0.8F, 1.4F);
				WebEffects.burst(level, at, 12, 0.4);
			}
		}

		if (!freeEnergy(p)) {
			d.energy -= cfg.energyCost;
		}
		d.cooldowns[cd] = cfg.cooldownTicks;
		sendStats(p, d, true);
		relayPose(p, d);
	}

	private static boolean multiWeb(ServerPlayer p, ServerLevel level, SpiderConfig c, Vec3 aim) {
		Vec3 look = aim.lengthSqr() > 1.0E-6 ? aim.normalize() : p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		AABB box = p.getBoundingBox().inflate(c.multiWebRange);
		List<LivingEntity> found = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, le -> le != p && le.isAlive())) {
			if (e instanceof ServerPlayer && !c.pullPlayers) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > c.multiWebRange || dist < 0.5 || to.scale(1.0 / dist).dot(look) < 0.3 || !p.hasLineOfSight(e)) {
				continue;
			}
			found.add(e);
		}
		if (found.isEmpty()) {
			return false;
		}
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		int n = Math.min(c.multiWebTargets, found.size());
		for (int i = 0; i < n; i++) {
			LivingEntity e = found.get(i);
			WebEffects.trapEntity(level, e, c.webDurationTicks);
			Vec3 center = e.getBoundingBox().getCenter();
			sendToWatchers(p, new ModPackets.WebShot(p.getId(), (i & 1) == 0, WebMode.TRAP.ordinal(), center.x, center.y, center.z), true);
		}
		level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.WEB_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static void endSlowMotion(MinecraftServer server) {
		if (slowMotionActive) {
			slowMotionActive = false;
			server.tickRateManager().setTickRate(20.0F);
		}
	}

	// ====================================================================== pose relay

	private static void onPose(ServerPlayer p, PoseData in) {
		SpiderConfig c = SpiderConfig.get();
		PlayerData d = data(p);
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		boolean gear = SpiderGear.hasGear(p);
		PoseData out = in;
		if (!gear) {
			out = new PoseData();
		} else {
			V3 v = in.velocity();
			if (v.length() > c.serverSpeedLimit) {
				deny(p, ModPackets.Deny.TOO_FAST, d.anchorMask());
				d.clearAnchor(0);
				d.clearAnchor(1);
				out = new PoseData();
			} else {
				if ((in.motion == MotionMode.WALL && !c.wallCrawl)
					|| (in.motion == MotionMode.CEILING && (!c.ceilingCrawl || !c.wallCrawl))
					|| (in.motion == MotionMode.GLIDE && !c.webWings)) {
					in.motion = MotionMode.NONE;
				}
				if (!c.webWings) {
					in.flags &= ~MotionMode.FLAG_WINGS;
				}
				in.flags &= ~MotionMode.FLAG_SHIELD;
				if (in.motion < 0 || in.motion > MotionMode.AIR) {
					in.motion = MotionMode.NONE;
				}
			}
		}
		d.pose = out;
		d.lastPoseTick = p.level().getServer().getTickCount();
		relayPose(p, d);
	}

	// ====================================================================== tick

	private static void tick(MinecraftServer server) {
		SpiderConfig c = SpiderConfig.get();
		int now = server.getTickCount();

		if (slowMotionActive && System.nanoTime() > slowMotionEndNanos) {
			endSlowMotion(server);
		}
		WebEffects.tickTempWebs(server, false);

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerData d = data(p);
			boolean gear = SpiderGear.hasGear(p);
			PowerAttributes.update(p, gear && p.isAlive());

			for (int i = 0; i < d.cooldowns.length; i++) {
				if (d.cooldowns[i] > 0) {
					d.cooldowns[i]--;
				}
			}
			if (d.cooldowns[PlayerData.CD_SHIELD] <= 0) {
				d.shieldHealth = 0;
			}
			if (d.reflexCooldown > 0) {
				d.reflexCooldown--;
			}

			// dimension change or death: ropes do not survive
			if (d.dimension != p.level().dimension() || !p.isAlive()) {
				d.dimension = p.level().dimension();
				if (d.hasAnchor()) {
					int mask = d.anchorMask();
					d.clearAnchor(0);
					d.clearAnchor(1);
					deny(p, ModPackets.Deny.INVALID_TARGET, mask);
				}
				if (!p.isAlive()) {
					d.pose = new PoseData();
					d.cooldowns[PlayerData.CD_SHIELD] = 0;
				}
			}

			// energy
			if (freeEnergy(p)) {
				d.energy = c.maxEnergy;
			} else {
				d.energy = Math.min(c.maxEnergy, d.energy + c.energyRegenPerTick);
			}
			if ((d.pose.flags & MotionMode.FLAG_WINGS) != 0) {
				if (!gear) {
					d.pose.flags &= ~MotionMode.FLAG_WINGS;
				} else if (!freeEnergy(p)) {
					d.energy -= c.wingsEnergyPerTick;
					if (d.energy <= 0) {
						d.energy = 0;
						d.pose.flags &= ~MotionMode.FLAG_WINGS;
						deny(p, ModPackets.Deny.NO_ENERGY, 0);
					}
				}
			}

			// ropes must stay valid: gear, anchor block, distance
			if (d.hasAnchor()) {
				if (!gear) {
					int mask = d.anchorMask();
					d.clearAnchor(0);
					d.clearAnchor(1);
					deny(p, ModPackets.Deny.NO_GEAR, mask);
					relayPose(p, d);
				} else if ((now + p.getId()) % 4 == 0) {
					checkAnchors(p, d, c);
				}
			}

			// stale movement state (client stopped sending)
			if (d.lastPoseActive && now - d.lastPoseTick > 40 && !d.hasAnchor()) {
				d.pose = new PoseData();
				relayPose(p, d);
			}

			sendStats(p, d, false);
		}
	}

	private static void checkAnchors(ServerPlayer p, PlayerData d, SpiderConfig c) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		int dropped = 0;
		for (int i = 0; i < 2; i++) {
			V3 a = d.anchors[i];
			if (a == null) {
				continue;
			}
			BlockPos bp = d.anchorBlocks[i];
			boolean broken = bp == null || !level.hasChunkAt(bp)
				|| level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
			boolean far = p.position().distanceTo(new Vec3(a.x, a.y, a.z)) > c.webRange + c.serverRangeSlack + 8.0;
			if (broken || far) {
				d.clearAnchor(i);
				dropped |= 1 << i;
			}
		}
		if (dropped != 0) {
			deny(p, ModPackets.Deny.INVALID_TARGET, dropped);
			relayPose(p, d);
		}
	}

	private static void sendStats(ServerPlayer p, PlayerData d, boolean force) {
		SpiderConfig c = SpiderConfig.get();
		int now = p.level().getServer().getTickCount();
		int sum = 0;
		for (int cd : d.cooldowns) {
			sum += cd > 0 ? 1 : 0;
		}
		boolean changed = Math.abs(d.energy - d.lastSentEnergy) >= 0.5F || sum != d.lastSentCooldownSum;
		if (!force && !changed && now - d.lastStatsTick < 20) {
			return;
		}
		if (!force && changed && now - d.lastStatsTick < 3) {
			return;
		}
		d.lastStatsTick = now;
		d.lastSentEnergy = (float) d.energy;
		d.lastSentCooldownSum = sum;
		send(p, new ModPackets.Stats((float) d.energy, (float) c.maxEnergy, d.cooldowns.clone()));
	}

	// ====================================================================== damage

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
			return true;
		}
		SpiderConfig c = SpiderConfig.get();
		if (!SpiderGear.hasGear(p)) {
			return true;
		}
		PlayerData d = data(p);

		if (source.is(DamageTypeTags.IS_FALL)) {
			if (c.webImpactLanding && amount >= 2.0F) {
				WebEffects.landing(level, p, amount);
			}
			return !c.fallProtection;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (d.cooldowns[PlayerData.CD_SHIELD] > 0 && d.shieldHealth > 0) {
			d.shieldHealth -= amount;
			WebEffects.shieldHit(level, p);
			if (d.shieldHealth <= 0) {
				d.cooldowns[PlayerData.CD_SHIELD] = 0;
				relayPose(p, d);
			}
			return false;
		}
		boolean attack = source.getEntity() != null || source.is(DamageTypeTags.IS_PROJECTILE);
		if (c.reflexes && attack && d.reflexCooldown <= 0 && p.getRandom().nextDouble() < c.reflexDodgeChance) {
			d.reflexCooldown = 12;
			WebEffects.dodge(level, p);
			return false;
		}
		return true;
	}
}
