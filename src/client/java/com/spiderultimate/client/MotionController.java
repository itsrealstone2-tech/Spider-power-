package com.spiderultimate.client;

import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.common.PowerAttributes;
import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import com.spiderultimate.physics.CrawlMath;
import com.spiderultimate.physics.FlightSolver;
import com.spiderultimate.physics.GlideSolver;
import com.spiderultimate.physics.Rope;
import com.spiderultimate.physics.SwingSolver;
import com.spiderultimate.physics.V3;
import com.spiderultimate.physics.ZipSolver;
import com.spiderultimate.server.PlayerData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the player's movement for web swinging, zipping, free flight, gliding and wall / ceiling crawling.
 *
 * <p>Vanilla design: the owning client simulates its own movement. At the start of every client tick the
 * physics core computes the displacement of this tick and writes it into the player's delta movement, vanilla
 * moves the player (with all block collisions), and at the end of the tick the difference between the intended
 * and the real displacement (a collision) is subtracted from the velocity. The player is never teleported.
 */
public final class MotionController {
	private static final V3[] HORIZONTAL = {new V3(0, 0, -1), new V3(1, 0, 0), new V3(0, 0, 1), new V3(-1, 0, 0)};

	private static final Rope[] ropes = new Rope[2];
	private static int mode = MotionMode.NONE;
	private static V3 vel = V3.ZERO;
	private static V3 startPos = V3.ZERO;
	private static V3 intended = V3.ZERO;
	private static V3 pendingVel = V3.ZERO;
	private static boolean controlling;

	private static V3 zipAnchor;
	private static int zipTicks;
	private static int zipStuck;

	private static V3 crawlNormal = V3.UP;
	private static V3 crawlHead = V3.UP;
	private static int lostContact;
	private static int attachCooldown;

	private static int airTicks;
	private static int groundTicks;
	private static boolean jumpWasDown;
	private static int trickTicks;
	private static int landingTicks;
	private static int tickCounter;
	private static boolean lastSentActive;
	private static int lastSentTick;

	private MotionController() {
	}

	// ====================================================================== queries

	public static boolean hasRope() {
		return ropes[0] != null || ropes[1] != null;
	}

	public static int mode() {
		return mode;
	}

	public static V3 velocity() {
		return vel;
	}

	public static boolean isCrawling() {
		return mode == MotionMode.WALL || mode == MotionMode.CEILING;
	}

	public static V3 crawlNormal() {
		return crawlNormal;
	}

	public static int landingTicks() {
		return landingTicks;
	}

	private static List<Rope> activeRopes() {
		List<Rope> l = new ArrayList<>(2);
		for (Rope r : ropes) {
			if (r != null) {
				l.add(r);
			}
		}
		return l;
	}

	// ====================================================================== lifecycle

	public static void reset() {
		ropes[0] = null;
		ropes[1] = null;
		mode = MotionMode.NONE;
		vel = V3.ZERO;
		controlling = false;
		zipAnchor = null;
		trickTicks = 0;
		landingTicks = 0;
		lastSentActive = false;
		ClientState.reset();
	}

	private static boolean canControl(LocalPlayer p) {
		return !p.isSpectator() && p.isAlive() && !p.isPassenger() && !p.getAbilities().flying
			&& !p.isFallFlying() && !p.isSleeping() && !p.isInWater() && !p.isInLava();
	}

	/** Start of a client tick: decide the movement of this tick. */
	public static void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p == null || level == null) {
			if (mode != MotionMode.NONE || hasRope()) {
				reset();
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		tickCounter++;
		controlling = false;
		if (attachCooldown > 0) {
			attachCooldown--;
		}
		if (landingTicks > 0) {
			landingTicks--;
		}
		if (trickTicks > 0) {
			trickTicks--;
		}
		SpiderConfig c = SpiderConfig.get();
		boolean gear = SpiderGear.hasGear(p);
		PowerAttributes.update(p, gear && p.isAlive());
		ClientState.tickStats(true);

		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;

		if (!gear || !canControl(p)) {
			if (mode != MotionMode.NONE || hasRope()) {
				stop(mc, p, true);
			}
			return;
		}

		// A web that was cancelled (server said no / the rope was used up) can leave the mode inconsistent.
		if (mode == MotionMode.SWING && !hasRope()) {
			enterAir(p);
		}
		if (mode == MotionMode.ZIP && zipAnchor == null) {
			enterAir(p);
		}

		switch (mode) {
			case MotionMode.SWING -> stepSwing(mc, p, c, jumpPressed);
			case MotionMode.ZIP -> stepZip(mc, p, c, jumpPressed);
			case MotionMode.AIR -> stepAir(mc, p, c, jumpPressed);
			case MotionMode.GLIDE -> stepGlide(mc, p, c, jumpPressed);
			case MotionMode.WALL, MotionMode.CEILING -> stepCrawl(mc, p, c, jumpPressed);
			default -> idle(mc, p, c);
		}
	}

	/** End of a client tick: reconcile with what vanilla really did, then publish the pose. */
	public static void onEndTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		if (controlling) {
			V3 actual = ClientUtil.v3(p.position()).sub(startPos);
			if (isCrawling()) {
				vel = V3.ZERO;
			} else {
				vel = pendingVel.add(actual.sub(intended));
				if (!vel.isFinite()) {
					vel = V3.ZERO;
				}
			}
			p.fallDistance = 0.0;
			if (mode == MotionMode.AIR || mode == MotionMode.GLIDE) {
				airTicks++;
				if (airTicks > 3 && (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable())) {
					land(mc, p);
				}
			} else if (mode == MotionMode.SWING) {
				groundTicks = p.onGround() ? groundTicks + 1 : 0;
				if (groundTicks > 14 && vel.horizontalLength() < 0.06) {
					release(mc, 3, false, true);
				}
			}
		}
		publish(mc, p);
	}

	// ====================================================================== idle: starting things

	private static void idle(Minecraft mc, LocalPlayer p, SpiderConfig c) {
		if (p.onGround()) {
			if (tryStartCrawl(mc, p, c)) {
				return;
			}
			return;
		}
		// airborne without a web: wings, wall grab
		vel = ClientUtil.v3(p.getDeltaMovement());
		if (ClientState.wingsOn && c.webWings && canGlide()) {
			mode = MotionMode.GLIDE;
			airTicks = 0;
			stepGlide(mc, p, c, false);
			return;
		}
		tryStartCrawl(mc, p, c);
	}

	private static boolean canGlide() {
		return ClientState.energy > 1.0F || (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative());
	}

	// ====================================================================== swing

	private static SwingSolver.Params swingParams(SpiderConfig c) {
		SwingSolver.Params sp = new SwingSolver.Params();
		sp.drag = c.swingDrag;
		sp.controlAccel = 0.010 * c.swingStrength * (c.airControl ? 1.0 : 0.3);
		sp.pumpAccel = 0.012 * c.swingStrength * (c.airControl ? 1.0 : 0.3);
		sp.maxSpeed = c.maxSwingSpeed;
		sp.reelSpeed = c.reelSpeed;
		sp.minRope = c.ropeMinLength;
		return sp;
	}

	private static void apply(LocalPlayer p, V3 displacement, V3 endVelocity) {
		startPos = ClientUtil.v3(p.position());
		intended = displacement;
		pendingVel = endVelocity;
		controlling = true;
		p.setDeltaMovement(displacement.x, displacement.y, displacement.z);
	}

	private static void stepSwing(Minecraft mc, LocalPlayer p, SpiderConfig c, boolean jumpPressed) {
		if (jumpPressed) {
			release(mc, 3, true, true);
			if (mode == MotionMode.AIR) {
				stepAir(mc, p, c, false);
			}
			return;
		}
		List<Rope> list = activeRopes();
		V3 ref = ClientUtil.bodyRef(p);
		for (Rope r : list) {
			if (ref.distanceTo(r.anchor) > c.webRange * 1.6 + 8.0) {
				release(mc, 3, false, true);
				return;
			}
		}
		V3 wish = ClientUtil.wish(mc, p);
		boolean reel = ModKeys.REEL.isDown();
		SwingSolver.Result r = SwingSolver.step(ref, vel, list, wish, reel, swingParams(c));
		apply(p, r.displacement, r.endVelocity);
	}

	/** A web hit its target: it becomes a rope (or starts a zip). */
	public static void attachRope(Minecraft mc, int slot, V3 anchor, V3 normal, boolean zip, boolean dual) {
		LocalPlayer p = mc.player;
		SpiderConfig c = SpiderConfig.get();
		if (p == null || !canControl(p)) {
			return;
		}
		V3 ref = ClientUtil.bodyRef(p);
		double dist = ref.distanceTo(anchor);
		if (isCrawling()) {
			vel = V3.ZERO;
		} else if (mode == MotionMode.NONE) {
			vel = ClientUtil.v3(p.getDeltaMovement());
		}
		if (zip) {
			ropes[0] = new Rope(0, anchor, dist);
			ropes[1] = null;
			zipAnchor = anchor.add(normal.scale(0.5));
			zipTicks = 0;
			zipStuck = 0;
			mode = MotionMode.ZIP;
			return;
		}
		if (!dual || c.maxAnchors < 2) {
			int other = 1 - slot;
			if (ropes[other] != null) {
				ropes[other] = null;
				if (ClientState.serverHasMod) {
					ClientPlayNetworking.send(new ModPackets.Release(1 << other, false));
				}
			}
		}
		ropes[slot] = new Rope(slot, anchor, Math.max(c.ropeMinLength, Math.min(dist, c.webRange)));
		if (mode != MotionMode.SWING) {
			if (p.onGround()) {
				vel = new V3(vel.x, Math.max(vel.y, 0.40), vel.z);
			}
			mode = MotionMode.SWING;
			groundTicks = 0;
		}
	}

	/** Lets go of the given ropes (bit 0 = left, bit 1 = right). */
	public static void release(Minecraft mc, int slotMask, boolean jump, boolean notify) {
		LocalPlayer p = mc.player;
		SpiderConfig c = SpiderConfig.get();
		for (int i = 0; i < 2; i++) {
			if ((slotMask & (1 << i)) != 0) {
				ropes[i] = null;
			}
		}
		if (notify && ClientState.serverHasMod) {
			ClientPlayNetworking.send(new ModPackets.Release(slotMask, jump));
		}
		if (!hasRope() && (mode == MotionMode.SWING || mode == MotionMode.ZIP)) {
			zipAnchor = null;
			if (jump && mode == MotionMode.SWING) {
				V3 h = vel.horizontal();
				V3 push = h.lengthSqr() > 1.0E-4 ? h.normalize().scale(0.06) : V3.ZERO;
				vel = vel.add(push).add(0, c.releaseJumpBoost, 0);
				fx(mc, p, 0);
			}
			if (p != null) {
				enterAir(p);
			} else {
				mode = MotionMode.AIR;
			}
		}
	}

	private static void enterAir(LocalPlayer p) {
		mode = MotionMode.AIR;
		airTicks = 0;
		zipAnchor = null;
	}

	/** Boosts the current movement along its direction (swing boost ability). */
	public static boolean boost(Minecraft mc, double factor, double maxSpeed) {
		if (mode == MotionMode.NONE || isCrawling()) {
			return false;
		}
		V3 v = vel;
		double s = v.length();
		LocalPlayer p = mc.player;
		if (s < 0.35 && p != null) {
			v = ClientUtil.v3(p.getLookAngle()).scale(0.35);
			s = 0.35;
		}
		double ns = Math.min(maxSpeed, s * factor);
		vel = v.scale(ns / s);
		return true;
	}

	public static void startTrick(int ticks) {
		trickTicks = ticks;
	}

	public static boolean airborneControlled() {
		return mode == MotionMode.SWING || mode == MotionMode.AIR || mode == MotionMode.GLIDE || mode == MotionMode.ZIP;
	}

	/** Leaps off the current surface along the look direction (wall launch ability). */
	public static boolean wallLaunch(Minecraft mc, double strength) {
		LocalPlayer p = mc.player;
		if (!isCrawling() || p == null) {
			return false;
		}
		vel = CrawlMath.wallLaunchVelocity(crawlNormal, ClientUtil.v3(p.getLookAngle()), strength);
		attachCooldown = 12;
		enterAir(p);
		return true;
	}

	/** Server refused something: drop the ropes it names. */
	public static void onDeny(Minecraft mc, ModPackets.Deny d) {
		if (d.slotMask() != 0) {
			WebShooter.cancelRopeWebs(d.slotMask());
			boolean had = false;
			for (int i = 0; i < 2; i++) {
				if ((d.slotMask() & (1 << i)) != 0 && ropes[i] != null) {
					had = true;
				}
			}
			if (had) {
				release(mc, d.slotMask(), false, false);
			}
		}
		if (d.reason() == ModPackets.Deny.NO_ENERGY) {
			ClientState.wingsOn = false;
			ClientState.energy = 0.0F;
		}
		String key = switch (d.reason()) {
			case ModPackets.Deny.NO_GEAR -> "spiderultimate.msg.no_gear";
			case ModPackets.Deny.NO_ENERGY -> "spiderultimate.msg.no_energy";
			case ModPackets.Deny.COOLDOWN -> "spiderultimate.msg.cooldown";
			case ModPackets.Deny.TOO_FAST -> "spiderultimate.msg.too_fast";
			case ModPackets.Deny.DISABLED -> "spiderultimate.msg.disabled";
			default -> "spiderultimate.msg.invalid_target";
		};
		ClientState.toast(Component.translatable(key).getString());
	}

	// ====================================================================== zip

	private static void stepZip(Minecraft mc, LocalPlayer p, SpiderConfig c, boolean jumpPressed) {
		if (jumpPressed) {
			release(mc, 3, false, true);
			stepAir(mc, p, c, false);
			return;
		}
		ZipSolver.Params zp = new ZipSolver.Params();
		ZipSolver.Result r = ZipSolver.step(ClientUtil.bodyRef(p), vel, zipAnchor, zp);
		apply(p, r.displacement, r.endVelocity);
		zipTicks++;
		if (zipTicks > 6 && p.horizontalCollision && r.displacement.length() > 0.4) {
			zipStuck++;
		}
		if (r.arrived || zipTicks > 90 || zipStuck > 4) {
			V3 end = r.endVelocity;
			release(mc, 3, false, true);
			vel = end.scale(0.9);
			fx(mc, p, 1);
		}
	}

	// ====================================================================== free flight and glide

	private static void stepAir(Minecraft mc, LocalPlayer p, SpiderConfig c, boolean jumpPressed) {
		if (airTicks > 5 && ClientState.wingsOn && c.webWings && canGlide()) {
			mode = MotionMode.GLIDE;
			stepGlide(mc, p, c, false);
			return;
		}
		if (airTicks > 2 && tryStartCrawl(mc, p, c)) {
			return;
		}
		FlightSolver.Params fp = new FlightSolver.Params();
		fp.control = c.airControl ? 0.014 : 0.003;
		fp.maxSpeed = Math.max(2.2, c.maxSwingSpeed * 1.4);
		V3 wish = ClientUtil.wish(mc, p);
		FlightSolver.Result r = FlightSolver.step(vel, wish, fp);
		apply(p, r.displacement, r.endVelocity);
	}

	private static void stepGlide(Minecraft mc, LocalPlayer p, SpiderConfig c, boolean jumpPressed) {
		boolean free = p.isCreative() && c.creativeInfiniteEnergy;
		if (!ClientState.wingsOn || !c.webWings || (!free && ClientState.energy <= 0.0F) || p.isShiftKeyDown()) {
			ClientState.wingsOn = false;
			enterAir(p);
			airTicks = 6;
			stepAir(mc, p, c, false);
			return;
		}
		if (!free) {
			ClientState.energy = Math.max(0.0F, ClientState.energy - (float) c.wingsEnergyPerTick);
		}
		GlideSolver.Params gp = new GlideSolver.Params();
		gp.maxSpeed = c.wingsMaxSpeed;
		GlideSolver.Result r = GlideSolver.step(vel, ClientUtil.v3(p.getLookAngle()), gp);
		apply(p, r.displacement, r.endVelocity);
	}

	/** Touching down: hand the momentum back to vanilla and show the landing. */
	private static void land(Minecraft mc, LocalPlayer p) {
		double impact = -vel.y;
		mode = MotionMode.NONE;
		controlling = false;
		V3 keep = new V3(vel.x * 0.6, 0, vel.z * 0.6);
		p.setDeltaMovement(keep.x, keep.y, keep.z);
		vel = V3.ZERO;
		ClientState.wingsOn = false;
		if (impact > 0.9 && SpiderConfig.get().cinematicEffects) {
			landingTicks = 12;
			Vec3 at = p.position();
			for (int i = 0; i < 18; i++) {
				double a = i * (Math.PI * 2 / 18);
				mc.level.addParticle(ParticleTypes.POOF, at.x + Math.cos(a) * 0.5, at.y + 0.1, at.z + Math.sin(a) * 0.5,
					Math.cos(a) * 0.15, 0.02, Math.sin(a) * 0.15);
			}
		}
	}

	/** Stops all superhero movement (gear removed, died, swimming...). */
	private static void stop(Minecraft mc, LocalPlayer p, boolean notify) {
		boolean had = hasRope();
		ropes[0] = null;
		ropes[1] = null;
		zipAnchor = null;
		if (had && notify && ClientState.serverHasMod) {
			ClientPlayNetworking.send(new ModPackets.Release(3, false));
		}
		if (mode != MotionMode.NONE && !isCrawling() && p.isAlive()) {
			p.setDeltaMovement(vel.x, vel.y, vel.z);
		}
		mode = MotionMode.NONE;
		vel = V3.ZERO;
		ClientState.wingsOn = false;
		controlling = false;
	}

	/** Force-stop used by the key handler (for example when crawl mode is switched off). */
	public static void detach(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p != null && isCrawling()) {
			vel = V3.ZERO;
			enterAir(p);
		}
	}

	// ====================================================================== crawl

	private static boolean blocked(ClientLevel level, LocalPlayer p, V3 dir, double dist) {
		AABB box = p.getBoundingBox();
		if (dir.y == 0) {
			box = box.deflate(0.0, 0.12, 0.0);
		} else {
			box = box.deflate(0.08, 0.0, 0.08);
		}
		return !level.noCollision(p, box.move(dir.x * dist, dir.y * dist, dir.z * dist));
	}

	private static boolean tryStartCrawl(Minecraft mc, LocalPlayer p, SpiderConfig c) {
		if (!c.wallCrawl || !ClientState.crawlMode || !c.autoCrawl || attachCooldown > 0 || p.isShiftKeyDown()) {
			return false;
		}
		ClientLevel level = mc.level;
		double f = ClientUtil.forwardInput(mc);
		double s = ClientUtil.strafeRightInput(mc);
		V3 look = CrawlMath.forwardFromYaw(p.getYRot());
		if (f > 0 || (f == 0 && s != 0 && !p.onGround())) {
			V3 want = f > 0 ? look : CrawlMath.rightFromYaw(p.getYRot()).scale(s);
			for (V3 d : HORIZONTAL) {
				if (want.dot(d) > 0.35 && blocked(level, p, d, 0.12)) {
					beginCrawl(mc, p, d.scale(-1));
					return true;
				}
			}
		}
		if (c.ceilingCrawl && !p.onGround() && p.getDeltaMovement().y > -0.02 && mc.options.keyJump.isDown()
			&& blocked(level, p, V3.UP, 0.12)) {
			beginCrawl(mc, p, new V3(0, -1, 0));
			return true;
		}
		return false;
	}

	private static void beginCrawl(Minecraft mc, LocalPlayer p, V3 normal) {
		boolean was = isCrawling();
		crawlNormal = normal;
		crawlHead = CrawlMath.isCeiling(normal) ? CrawlMath.forwardFromYaw(p.getYRot()) : V3.UP;
		mode = CrawlMath.isCeiling(normal) ? MotionMode.CEILING : MotionMode.WALL;
		vel = V3.ZERO;
		lostContact = 0;
		if (!was) {
			p.setDeltaMovement(0, 0, 0);
			fx(mc, p, 2);
		}
	}

	private static void stepCrawl(Minecraft mc, LocalPlayer p, SpiderConfig c, boolean jumpPressed) {
		ClientLevel level = mc.level;
		V3 n = crawlNormal;
		boolean ceiling = CrawlMath.isCeiling(n);
		if (!c.wallCrawl || (ceiling && !c.ceilingCrawl) || !ClientState.crawlMode || p.isShiftKeyDown()) {
			vel = V3.ZERO;
			enterAir(p);
			return;
		}
		if (jumpPressed && c.wallJump) {
			vel = CrawlMath.wallJumpVelocity(n, ClientUtil.v3(p.getLookAngle()), c.wallJumpStrength);
			attachCooldown = 10;
			enterAir(p);
			fx(mc, p, 0);
			stepAir(mc, p, c, false);
			return;
		}
		V3 into = n.scale(-1);
		if (blocked(level, p, into, 0.14)) {
			lostContact = 0;
		} else {
			lostContact++;
		}
		double f = ClientUtil.forwardInput(mc);
		double s = ClientUtil.strafeRightInput(mc);
		V3 mv = CrawlMath.surfaceVelocity(n, p.getYRot(), f, s, c.crawlSpeed);

		if (lostContact >= 3) {
			// ran out of surface: climb over the edge when going up, otherwise let go
			if (!ceiling && f > 0) {
				vel = into.scale(0.16).add(0, 0.30, 0);
			} else {
				vel = V3.ZERO;
			}
			attachCooldown = 6;
			enterAir(p);
			stepAir(mc, p, c, false);
			return;
		}
		if (!ceiling) {
			if (p.onGround() && f < 0) {
				vel = V3.ZERO;
				mode = MotionMode.NONE;
				controlling = false;
				return;
			}
			if (c.ceilingCrawl && f > 0 && blocked(level, p, V3.UP, 0.12)) {
				beginCrawl(mc, p, new V3(0, -1, 0));
				return;
			}
			if (mv.horizontalLength() > 0.01) {
				for (V3 d : HORIZONTAL) {
					if (d.dot(n) < 0.9 && d.dot(into) < 0.9 && mv.dot(d) > 0.03 && blocked(level, p, d, 0.1)) {
						beginCrawl(mc, p, d.scale(-1));
						return;
					}
				}
			}
		} else {
			V3 mh = mv.horizontal();
			if (mh.length() > 0.01) {
				for (V3 d : HORIZONTAL) {
					if (mh.dot(d) > 0.03 && blocked(level, p, d, 0.1)) {
						beginCrawl(mc, p, d.scale(-1));
						return;
					}
				}
			}
		}

		V3 stick = CrawlMath.stickVelocity(n, 0.10);
		V3 total = mv.add(stick);
		V3 targetHead = mv.length() > 0.01 ? mv.normalize() : (ceiling ? CrawlMath.forwardFromYaw(p.getYRot()) : V3.UP);
		crawlHead = crawlHead.lerp(targetHead, 0.3);
		if (crawlHead.lengthSqr() < 1.0E-6) {
			crawlHead = targetHead;
		}
		crawlHead = crawlHead.normalize();
		apply(p, total, V3.ZERO);
	}

	// ====================================================================== effects, pose

	private static void fx(Minecraft mc, LocalPlayer p, int kind) {
		if (mc.level == null || !SpiderConfig.get().cinematicEffects) {
			return;
		}
		Vec3 at = p.position().add(0, 1.0, 0);
		int n = kind == 2 ? 6 : 10;
		for (int i = 0; i < n; i++) {
			mc.level.addParticle(kind == 1 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_ASH, at.x, at.y, at.z,
				(mc.level.random.nextDouble() - 0.5) * 0.25, (mc.level.random.nextDouble() - 0.5) * 0.25,
				(mc.level.random.nextDouble() - 0.5) * 0.25);
		}
	}

	/** Copies the movement state into the pose that the renderers use and sends it to the server. */
	private static void publish(Minecraft mc, LocalPlayer p) {
		PoseData lp = ClientState.localPose;
		lp.motion = mode;
		lp.flags = (mode == MotionMode.GLIDE ? MotionMode.FLAG_WINGS : 0)
			| (ClientState.cooldowns[PlayerData.CD_SHIELD] > 0 ? MotionMode.FLAG_SHIELD : 0);
		lp.vx = (float) vel.x;
		lp.vy = (float) vel.y;
		lp.vz = (float) vel.z;
		lp.nx = (float) crawlNormal.x;
		lp.ny = (float) crawlNormal.y;
		lp.nz = (float) crawlNormal.z;
		lp.hx = (float) crawlHead.x;
		lp.hy = (float) crawlHead.y;
		lp.hz = (float) crawlHead.z;
		lp.trick = trickTicks > 0 ? 1.0F - trickTicks / 14.0F : -1.0F;
		lp.anchor[0] = ropes[0] != null ? ropes[0].anchor : null;
		lp.anchor[1] = ropes[1] != null ? ropes[1].anchor : null;

		if (!ClientState.serverHasMod) {
			return;
		}
		boolean active = (lp.flags & ~MotionMode.FLAG_SHIELD) != 0 || lp.motion != MotionMode.NONE || lp.trick >= 0.0F
			|| lp.anchor[0] != null || lp.anchor[1] != null;
		if (active || lastSentActive) {
			if (tickCounter - lastSentTick >= 2 || active != lastSentActive) {
				PoseData out = lp.copy();
				out.flags &= ~MotionMode.FLAG_SHIELD;
				ClientPlayNetworking.send(new ModPackets.PoseUpdate(out));
				lastSentTick = tickCounter;
				lastSentActive = active;
			}
		}
	}
}
