package com.spiderultimate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spiderultimate.client.ClientState;
import com.spiderultimate.client.ClientUtil;
import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.physics.V3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.Map;

/** Draws web ropes, flying webs, web wings and the web shield into the world. */
public final class SpiderRenderer {
	private static final QuadBatch BATCH = new QuadBatch();

	private SpiderRenderer() {
	}

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(SpiderRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		SpiderConfig c = SpiderConfig.get();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack ms = ctx.matrices();
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		ms.pushPose();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4fc m = ms.last().pose();

		drawPlayer(mc, m, cam, mc.player, ClientState.localPose, partial, c);
		for (Map.Entry<Integer, ClientState.Remote> e : ClientState.remote.entrySet()) {
			Entity ent = mc.level.getEntity(e.getKey());
			if (ent == null || ent == mc.player) {
				continue;
			}
			drawPlayer(mc, m, cam, ent, e.getValue().pose, partial, c);
		}
		for (ClientState.FlyingWeb w : ClientState.flying) {
			Entity shooter = mc.level.getEntity(w.entityId);
			if (shooter == null || w.cancelled) {
				continue;
			}
			// the rope of an arrived local web is drawn by the pose, not here
			if (w.handled && w.slot >= 0) {
				continue;
			}
			Vec3 from = ClientUtil.handPos(shooter, w.left, partial);
			Vec3 to = ClientUtil.vec(w.target);
			float t = w.progress(partial);
			Vec3 tip = from.add(to.subtract(from).scale(t));
			rope(m, cam, from, tip, 0.25 * (1.0 - t), (float) c.ropeThickness * 1.1F);
		}
		ms.popPose();
		BATCH.flush(mc);
	}

	private static void drawPlayer(Minecraft mc, Matrix4fc m, Vec3 cam, Entity ent, PoseData pose, float partial, SpiderConfig c) {
		for (int slot = 0; slot < 2; slot++) {
			V3 a = pose.anchor[slot];
			if (a == null) {
				continue;
			}
			Vec3 hand = ClientUtil.handPos(ent, slot == 0, partial);
			Vec3 anchor = ClientUtil.vec(a);
			double slack = pose.motion == MotionMode.SWING ? 0.0 : 0.2;
			rope(m, cam, hand, anchor, slack, (float) c.ropeThickness);
		}
		ClientState.Remote r = ClientState.remote.get(ent.getId());
		float glide = r == null ? 0.0F : r.prevGlide + (r.glide - r.prevGlide) * partial;
		if ((pose.flags & MotionMode.FLAG_WINGS) != 0 || glide > 0.05F) {
			wings(m, cam, ent, partial, Math.max(glide, (pose.flags & MotionMode.FLAG_WINGS) != 0 ? 0.3F : 0.0F));
		}
		if ((pose.flags & MotionMode.FLAG_SHIELD) != 0) {
			shield(mc, m, cam, ent, partial);
		}
	}

	// ------------------------------------------------------------------ primitives

	/** A web strand: a bright core with a faint halo, always facing the camera. */
	private static void rope(Matrix4fc m, Vec3 cam, Vec3 a, Vec3 b, double slack, float width) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.05) {
			return;
		}
		int n = Math.max(2, Math.min(28, (int) (len / 1.2)));
		Vec3 prev = a;
		for (int i = 1; i <= n; i++) {
			double u = i / (double) n;
			double sag = slack * Math.sin(Math.PI * u) * Math.min(1.0, len * 0.08);
			Vec3 p = a.add(d.scale(u)).add(0, -sag, 0);
			strip(m, cam, prev, p, width * 2.6F, 0.82F, 0.92F, 1.0F, 0.22F);
			strip(m, cam, prev, p, width, 1.0F, 1.0F, 1.0F, 0.95F);
			prev = p;
		}
	}

	private static void strip(Matrix4fc m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		Vec3 mid = a.add(b).scale(0.5).subtract(cam);
		double dist = mid.length();
		Vec3 side = dir.cross(mid);
		if (side.lengthSqr() < 1.0E-10) {
			side = dir.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0E-10) {
				return;
			}
		}
		side = side.normalize().scale(width * 0.5 * (1.0 + dist * 0.02));
		BATCH.quad(m,
			(float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z),
			(float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z),
			(float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z),
			(float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z),
			r, g, bl, alpha);
	}

	private static void wings(Matrix4fc m, Vec3 cam, Entity ent, float partial, float open) {
		Vec3 pos = ent.getPosition(partial).add(0, ent.getBbHeight() * 0.68, 0);
		Vec3 look = ent.getViewVector(partial);
		Vec3 fwd = new Vec3(look.x, 0, look.z);
		fwd = fwd.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
		float span = 1.9F * open;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 shoulder = pos.add(right.scale(0.22 * side)).add(fwd.scale(-0.1));
			Vec3 tip = pos.add(right.scale(span * side)).add(fwd.scale(-0.55 * open)).add(0, 0.12 * open, 0);
			Vec3 tipBack = pos.add(right.scale(span * 0.7 * side)).add(fwd.scale(-1.5 * open)).add(0, -0.2 * open, 0);
			Vec3 hip = pos.add(right.scale(0.2 * side)).add(fwd.scale(-0.55)).add(0, -0.7, 0);
			BATCH.quad(m,
				(float) shoulder.x, (float) shoulder.y, (float) shoulder.z,
				(float) tip.x, (float) tip.y, (float) tip.z,
				(float) tipBack.x, (float) tipBack.y, (float) tipBack.z,
				(float) hip.x, (float) hip.y, (float) hip.z,
				0.85F, 0.93F, 1.0F, 0.38F);
			// webbing: spokes from the shoulder to the trailing edge
			for (int k = 0; k <= 3; k++) {
				double u = k / 3.0;
				Vec3 edge = tipBack.add(hip.subtract(tipBack).scale(u));
				spoke(m, cam, shoulder, edge);
			}
			spoke(m, cam, tip, tipBack);
		}
	}

	private static void spoke(Matrix4fc m, Vec3 cam, Vec3 a, Vec3 b) {
		strip(m, cam, a, b, 0.03F, 1.0F, 1.0F, 1.0F, 0.85F);
	}

	private static void shield(Minecraft mc, Matrix4fc m, Vec3 cam, Entity ent, float partial) {
		Vec3 center = ent.getPosition(partial).add(0, ent.getBbHeight() * 0.5, 0);
		double t = (mc.level.getGameTime() + partial) * 0.06;
		double radius = 1.25;
		int segs = 28;
		for (int ring = 0; ring < 3; ring++) {
			double tilt = t + ring * Math.PI / 3.0;
			Vec3 u = new Vec3(1, 0, 0);
			Vec3 v = new Vec3(0, Math.cos(tilt), Math.sin(tilt));
			if (ring == 0) {
				u = new Vec3(Math.cos(t), 0, -Math.sin(t));
				v = new Vec3(Math.sin(t), 0, Math.cos(t));
			}
			Vec3 prev = center.add(u.scale(radius));
			for (int i = 1; i <= segs; i++) {
				double a = i * Math.PI * 2.0 / segs;
				Vec3 p = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
				strip(m, cam, prev, p, 0.05F, 0.85F, 0.95F, 1.0F, 0.55F);
				prev = p;
			}
		}
	}

	public static void close() {
		// the batch owns native buffers that are released with the game; nothing to do per world
	}
}
