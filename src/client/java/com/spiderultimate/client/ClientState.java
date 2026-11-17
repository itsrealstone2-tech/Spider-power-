package com.spiderultimate.client;

import com.spiderultimate.common.Ability;
import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.common.WebMode;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.physics.V3;
import com.spiderultimate.server.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Client side state shared by the controllers, the HUD and the renderers. */
public final class ClientState {
	public static WebMode mode = WebMode.SINGLE;
	public static Ability ability = Ability.SWING_BOOST;
	public static boolean crawlMode = true;
	public static boolean senseOn = true;
	public static boolean wingsOn;
	/** Which hand fires next when hands alternate. */
	public static boolean nextLeft = true;
	/** True while the server we are connected to understands our packets. */
	public static boolean serverHasMod;

	// Authoritative values come from the server (Stats packet); in between they are predicted locally.
	public static float energy = 100.0F;
	public static float maxEnergy = 100.0F;
	public static final int[] cooldowns = new int[PlayerData.CD_COUNT];

	/** Movement state of the local player, produced by {@link MotionController}. */
	public static final PoseData localPose = new PoseData();
	/** Short message above the hotbar, with its remaining ticks. */
	public static String message = "";
	public static int messageTicks;

	/** A web that is flying towards its target. */
	public static final class FlyingWeb {
		public final int entityId;
		public final boolean left;
		public final int mode;
		public final V3 target;
		public final int duration;
		public int age;
		/** Rope slot for local rope webs, -1 otherwise. */
		public final int slot;
		public boolean cancelled;
		public boolean handled;
		/** Surface normal at the target (local ropes only). */
		public V3 normal = V3.UP;

		public FlyingWeb(int entityId, boolean left, int mode, V3 target, int duration, int slot) {
			this.entityId = entityId;
			this.left = left;
			this.mode = mode;
			this.target = target;
			this.duration = Math.max(1, duration);
			this.slot = slot;
		}

		public float progress(float partial) {
			return Math.min(1.0F, (age + partial) / duration);
		}
	}

	public static final List<FlyingWeb> flying = new ArrayList<>();

	/** What we know about another player's superhero state. */
	public static final class Remote {
		public PoseData pose = new PoseData();
		public int lastUpdate;
		public int shotTick = -1000;
		public boolean shotLeft;
		// smoothed crawl orientation for rendering
		public V3 normal = V3.UP;
		public V3 prevNormal = V3.UP;
		public V3 head = V3.UP;
		public V3 prevHead = V3.UP;
		public float blend;
		public float prevBlend;
		public float glide;
		public float prevGlide;
		public float trickSpin;
		public int age;
		/** Smoothed speed in blocks per tick (for limb animation). */
		public double speed;
		public double lastX, lastY, lastZ;
		public boolean hasLast;
	}

	public static final Map<Integer, Remote> remote = new HashMap<>();

	private ClientState() {
	}

	public static Remote remote(int entityId) {
		return remote.computeIfAbsent(entityId, k -> new Remote());
	}

	public static void toast(String text) {
		message = text;
		messageTicks = 50;
	}

	public static void reset() {
		wingsOn = false;
		flying.clear();
		remote.clear();
		localPose.motion = MotionMode.NONE;
		localPose.flags = 0;
		localPose.anchor[0] = null;
		localPose.anchor[1] = null;
		localPose.trick = -1.0F;
		energy = (float) SpiderConfig.get().maxEnergy;
		maxEnergy = energy;
		java.util.Arrays.fill(cooldowns, 0);
	}

	/** Local prediction of energy and cooldowns; the server's Stats packets overwrite it. */
	public static void tickStats(boolean regen) {
		SpiderConfig c = SpiderConfig.get();
		maxEnergy = (float) c.maxEnergy;
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}
		if (regen) {
			energy = Math.min(maxEnergy, energy + (float) c.energyRegenPerTick);
		}
		if (messageTicks > 0) {
			messageTicks--;
		}
	}

	public static void tickFlying() {
		Iterator<FlyingWeb> it = flying.iterator();
		while (it.hasNext()) {
			FlyingWeb w = it.next();
			w.age++;
			if (w.cancelled || w.age > w.duration + 2) {
				it.remove();
			}
		}
	}

	/** Advances the render smoothing of the given remote state by one tick. */
	public static void tickRemote(Remote r, boolean isLocal) {
		PoseData p = isLocal ? localPose : r.pose;
		r.age++;
		r.prevNormal = r.normal;
		r.prevHead = r.head;
		r.prevBlend = r.blend;
		r.prevGlide = r.glide;
		boolean crawl = p.motion == MotionMode.WALL || p.motion == MotionMode.CEILING;
		V3 targetN = crawl ? p.normal().normalize() : V3.UP;
		V3 targetH = crawl ? p.head().normalize() : V3.UP;
		if (crawl && r.blend < 0.05F) {
			r.normal = targetN;
			r.head = targetH;
		} else {
			r.normal = slerpish(r.normal, targetN, 0.3);
			r.head = slerpish(r.head, targetH, 0.3);
		}
		r.blend += ((crawl ? 1.0F : 0.0F) - r.blend) * 0.3F;
		boolean glide = p.motion == MotionMode.GLIDE;
		r.glide += ((glide ? 1.0F : 0.0F) - r.glide) * 0.25F;
		if (p.trick >= 0.0F) {
			r.trickSpin = p.trick;
		}
	}

	private static V3 slerpish(V3 a, V3 b, double t) {
		V3 v = a.lerp(b, t);
		return v.lengthSqr() < 1.0E-6 ? b : v.normalize();
	}

	public static void forgetStale(int now) {
		remote.values().removeIf(r -> r.age > 0 && now - r.lastUpdate > 200 && !r.pose.active());
	}
}
