package com.spiderultimate.client;

import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.common.WebMode;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import com.spiderultimate.physics.AutoAim;
import com.spiderultimate.physics.V3;
import com.spiderultimate.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Aiming, firing and the flight of the web projectiles (client side). */
public final class WebShooter {
	/** A resolved target: a point in the world, the surface normal (blocks) or the entity id (-1 for none). */
	public record Aim(V3 point, V3 normal, int entity, boolean block) {
	}

	private WebShooter() {
	}

	// ---------------------------------------------------------------- aiming

	private static Aim cast(Minecraft mc, Vec3 eye, Vec3 dir, double range, boolean entities) {
		ClientLevel level = mc.level;
		LocalPlayer p = mc.player;
		Vec3 end = eye.add(dir.scale(range));
		BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		boolean hitBlock = bh.getType() != HitResult.Type.MISS;
		double blockDist = hitBlock ? bh.getLocation().distanceTo(eye) : range;

		if (entities) {
			Entity best = null;
			Vec3 bestPoint = null;
			double bestDist = blockDist;
			AABB sweep = new AABB(eye, end).inflate(1.5);
			for (Entity e : level.getEntities(p, sweep, ent -> isTargetable(p, ent))) {
				Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
				if (hit.isPresent()) {
					double d = hit.get().distanceTo(eye);
					if (d < bestDist) {
						bestDist = d;
						best = e;
						bestPoint = hit.get();
					}
				}
			}
			if (best != null) {
				return new Aim(ClientUtil.v3(bestPoint), V3.UP, best.getId(), false);
			}
		}
		if (!hitBlock) {
			return null;
		}
		return new Aim(ClientUtil.v3(bh.getLocation()),
			new V3(bh.getDirection().getStepX(), bh.getDirection().getStepY(), bh.getDirection().getStepZ()), -1, true);
	}

	private static boolean isTargetable(LocalPlayer p, Entity e) {
		if (e == p || !e.isAlive() || e.isSpectator()) {
			return false;
		}
		if (e instanceof Player && !SpiderConfig.get().pullPlayers) {
			return false;
		}
		return e instanceof LivingEntity || e instanceof ItemEntity;
	}

	/** Looks for a block to hang a rope on; when the crosshair does not give a good one the aim assist searches. */
	private static Aim ropeTarget(Minecraft mc, Vec3 eye, Vec3 look, double range, boolean assist) {
		Aim direct = cast(mc, eye, look, range, false);
		V3 eyeV = ClientUtil.v3(eye);
		V3 lookV = ClientUtil.v3(look);
		if (direct != null && (!assist || AutoAim.score(eyeV, direct.point(), lookV, range) > -1.0E8)) {
			return direct;
		}
		if (!assist) {
			return direct;
		}
		Aim best = null;
		double bestScore = -1.0E8;
		for (V3 d : AutoAim.candidates(lookV)) {
			Aim a = cast(mc, eye, ClientUtil.vec(d), range, false);
			if (a == null) {
				continue;
			}
			double sc = AutoAim.score(eyeV, a.point(), lookV, range);
			if (sc > bestScore) {
				bestScore = sc;
				best = a;
			}
		}
		return best != null ? best : direct;
	}

	private static Aim entityTarget(Minecraft mc, Vec3 eye, Vec3 look, double range) {
		Aim direct = cast(mc, eye, look, range, true);
		if (direct != null && direct.entity() >= 0) {
			return direct;
		}
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		Entity best = null;
		double bestDot = 0.965;
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(range), ent -> isTargetable(p, ent))) {
			Vec3 center = e.getBoundingBox().getCenter();
			Vec3 to = center.subtract(eye);
			double d = to.length();
			if (d > range || d < 0.5) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot) {
				BlockHitResult los = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (los.getType() == HitResult.Type.MISS) {
					bestDot = dot;
					best = e;
				}
			}
		}
		if (best != null) {
			Vec3 c = best.getBoundingBox().getCenter();
			return new Aim(ClientUtil.v3(c), V3.UP, best.getId(), false);
		}
		return direct;
	}

	private static Vec3 rotateYaw(Vec3 v, double degrees) {
		double r = Math.toRadians(degrees);
		double c = Math.cos(r);
		double s = Math.sin(r);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	// ---------------------------------------------------------------- firing

	/** The hand that belongs to a use of the Web Shooter item. */
	public static boolean leftForHand(LocalPlayer p, InteractionHand hand) {
		boolean mainIsLeft = p.getMainArm() == HumanoidArm.LEFT;
		return (hand == InteractionHand.MAIN_HAND) == mainIsLeft;
	}

	/**
	 * Fires the web shooter.
	 *
	 * @param forceLeft true / false to force a hand, null to alternate (or use the right hand)
	 * @param swingCmd  true for the swing key: always a rope web, with aim assist
	 */
	public static boolean fire(Minecraft mc, WebMode mode, Boolean forceLeft, boolean swingCmd) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || p.isSpectator() || !p.isAlive()) {
			return false;
		}
		SpiderConfig c = SpiderConfig.get();
		if (!SpiderGear.hasGear(p)) {
			ClientState.toast(Component.translatable("spiderultimate.msg.no_gear").getString());
			return false;
		}
		boolean left = forceLeft != null ? forceLeft : (c.alternateHands && ClientState.nextLeft);
		boolean ok;
		if (mode == WebMode.DUAL) {
			boolean a = shootOne(mc, mode, true, swingCmd, -7.0);
			boolean b = shootOne(mc, mode, false, swingCmd, 7.0);
			ok = a || b;
		} else {
			ok = shootOne(mc, mode, left, swingCmd, 0.0);
			if (ok && forceLeft == null && c.alternateHands) {
				ClientState.nextLeft = !ClientState.nextLeft;
			}
		}
		return ok;
	}

	private static boolean shootOne(Minecraft mc, WebMode mode, boolean left, boolean swingCmd, double yawOffset) {
		LocalPlayer p = mc.player;
		SpiderConfig c = SpiderConfig.get();
		int hand = left ? com.spiderultimate.server.PlayerData.CD_LEFT : com.spiderultimate.server.PlayerData.CD_RIGHT;
		if (ClientState.cooldowns[hand] > 0) {
			return false;
		}
		double cost = mode == WebMode.DUAL ? mode.cost(c) * 0.5 : mode.cost(c);
		boolean free = p.isCreative() && c.creativeInfiniteEnergy;
		if (!free && ClientState.energy < cost) {
			ClientState.toast(Component.translatable("spiderultimate.msg.no_energy").getString());
			return false;
		}
		if (!c.midairShooting && !p.onGround() && !MotionController.hasRope()) {
			ClientState.toast(Component.translatable("spiderultimate.msg.no_midair").getString());
			return false;
		}

		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		if (yawOffset != 0.0) {
			look = rotateYaw(look, yawOffset);
		}
		Aim aim;
		switch (mode) {
			case SINGLE, DUAL, ZIP -> aim = ropeTarget(mc, eye, look, c.webRange, c.autoAim && (swingCmd || mode != WebMode.SINGLE || true));
			case RAPID -> {
				aim = cast(mc, eye, look, c.webRange, true);
				if (aim == null) {
					Vec3 far = eye.add(look.scale(c.webRange));
					aim = new Aim(ClientUtil.v3(far), V3.UP, -1, false);
				}
			}
			default -> aim = entityTarget(mc, eye, look, c.webRange);
		}
		boolean valid = aim != null && switch (mode) {
			case SINGLE, DUAL, ZIP -> aim.block();
			case PULL -> aim.entity() >= 0;
			case TRAP, BOMB -> aim.entity() >= 0 || aim.block();
			case RAPID -> true;
		};
		if (!valid) {
			ClientState.toast(Component.translatable("spiderultimate.msg.no_target").getString());
			return false;
		}

		int slot = mode == WebMode.ZIP ? 0 : (left ? 0 : 1);
		Vec3 target = ClientUtil.vec(aim.point());
		Vec3 start = ClientUtil.handPos(p, left, 1.0F);
		int ticks = Math.max(1, (int) Math.ceil(start.distanceTo(target) / c.webProjectileSpeed));
		ClientState.FlyingWeb fw = new ClientState.FlyingWeb(p.getId(), left, mode.ordinal(), aim.point(), ticks,
			mode.isRopeMode() ? slot : -1);
		fw.normal = aim.normal();
		ClientState.flying.add(fw);

		ClientState.cooldowns[hand] = mode == WebMode.DUAL ? c.webCooldownTicks : mode.cooldown(c);
		if (!free) {
			ClientState.energy -= (float) cost;
		}
		ClientState.Remote self = ClientState.remote(p.getId());
		self.shotTick = self.age;
		self.shotLeft = left;

		if (ClientState.serverHasMod) {
			ClientPlayNetworking.send(new ModPackets.WebFire(mode.ordinal(), left, slot, eye.x, eye.y, eye.z,
				target.x, target.y, target.z, aim.entity()));
		} else {
			mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.WEB_SHOOT, SoundSource.PLAYERS, 0.8F, 1.0F, false);
		}
		return true;
	}

	/** Quick zip ability: picks a rope target with aim assist and sends a web towards it. Returns the target. */
	public static V3 quickZip(Minecraft mc) {
		LocalPlayer p = mc.player;
		SpiderConfig c = SpiderConfig.get();
		Vec3 eye = p.getEyePosition();
		Aim aim = ropeTarget(mc, eye, p.getLookAngle(), c.webRange, true);
		if (aim == null || !aim.block()) {
			ClientState.toast(Component.translatable("spiderultimate.msg.no_target").getString());
			return null;
		}
		Vec3 start = ClientUtil.handPos(p, false, 1.0F);
		int ticks = Math.max(1, (int) Math.ceil(start.distanceTo(ClientUtil.vec(aim.point())) / c.webProjectileSpeed));
		ClientState.FlyingWeb fw = new ClientState.FlyingWeb(p.getId(), false, WebMode.ZIP.ordinal(), aim.point(), ticks, 0);
		fw.normal = aim.normal();
		ClientState.flying.add(fw);
		if (!ClientState.serverHasMod) {
			mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.WEB_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F, false);
		}
		return aim.point();
	}

	// ---------------------------------------------------------------- flight

	/** Called when the server tells that somebody else shot a web. */
	public static void onWebShot(Minecraft mc, ModPackets.WebShot s) {
		if (mc.level == null || mc.player == null || s.entityId() == mc.player.getId()) {
			return;
		}
		Entity shooter = mc.level.getEntity(s.entityId());
		if (shooter == null) {
			return;
		}
		Vec3 target = new Vec3(s.tx(), s.ty(), s.tz());
		Vec3 start = ClientUtil.handPos(shooter, s.left(), 1.0F);
		int ticks = Math.max(1, (int) Math.ceil(start.distanceTo(target) / SpiderConfig.get().webProjectileSpeed));
		ClientState.flying.add(new ClientState.FlyingWeb(s.entityId(), s.left(), s.mode(), ClientUtil.v3(target), ticks, -1));
		ClientState.Remote r = ClientState.remote(s.entityId());
		r.shotTick = r.age;
		r.shotLeft = s.left();
	}

	/** Advances the flying webs; a web of the local player that arrives turns into a rope (or an effect). */
	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			ClientState.flying.clear();
			return;
		}
		ClientState.tickFlying();
		for (ClientState.FlyingWeb w : new java.util.ArrayList<>(ClientState.flying)) {
			if (w.cancelled || w.handled || w.age < w.duration) {
				continue;
			}
			w.handled = true;
			Vec3 at = ClientUtil.vec(w.target);
			boolean local = w.entityId == p.getId();
			boolean rope = local && w.slot >= 0;
			if (rope) {
				if (anchorStillSolid(mc, w)) {
					boolean zip = w.mode == WebMode.ZIP.ordinal();
					MotionController.attachRope(mc, w.slot, w.target, w.normal, zip, w.mode == WebMode.DUAL.ordinal());
					mc.level.addParticle(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 0, 0.02, 0);
					for (int i = 0; i < 6; i++) {
						mc.level.addParticle(ParticleTypes.POOF, at.x, at.y, at.z,
							(mc.level.random.nextDouble() - 0.5) * 0.1, (mc.level.random.nextDouble() - 0.5) * 0.1,
							(mc.level.random.nextDouble() - 0.5) * 0.1);
					}
					if (!ClientState.serverHasMod) {
						mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.WEB_HIT, SoundSource.PLAYERS, 0.6F, 1.0F, false);
					}
				} else {
					w.cancelled = true;
				}
			} else if (!ClientState.serverHasMod && local) {
				mc.level.addParticle(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 0, 0.02, 0);
			}
		}
	}

	private static boolean anchorStillSolid(Minecraft mc, ClientState.FlyingWeb w) {
		BlockPos bp = BlockPos.containing(w.target.x - w.normal.x * 0.05, w.target.y - w.normal.y * 0.05, w.target.z - w.normal.z * 0.05);
		return !mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty();
	}

	/** A rope web that was refused by the server never becomes a rope. */
	public static void cancelRopeWebs(int slotMask) {
		for (ClientState.FlyingWeb w : ClientState.flying) {
			if (w.slot >= 0 && (slotMask & (1 << w.slot)) != 0 && w.entityId >= 0) {
				w.cancelled = true;
			}
		}
	}
}
