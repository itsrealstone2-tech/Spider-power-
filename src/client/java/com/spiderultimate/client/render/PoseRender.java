package com.spiderultimate.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spiderultimate.client.ClientState;
import com.spiderultimate.client.MotionController;
import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.physics.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pose and orientation of player models: crawling on walls and ceilings, gliding, air tricks, arms that hold the
 * web. The mixins call these methods; the numbers come from {@link PoseData} (local or relayed by the server).
 */
public final class PoseRender {
	/** What the mixins need to know about one render state. */
	public static final class Info {
		public float blend;
		public V3 normal = V3.UP;
		public V3 head = V3.UP;
		public float glide;
		public float trick = -1.0F;
		public int anchors;
		public boolean wallOrCeiling;
		public boolean ceiling;
		public float shoot;
		public boolean shootLeft;
		public float speed;
		public float landing;
		public float age;
	}

	private static final Map<AvatarRenderState, Info> INFO = new WeakHashMap<>();

	private PoseRender() {
	}

	public static void capture(Avatar entity, AvatarRenderState state, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !SpiderConfig.get().cinematicEffects) {
			INFO.remove(state);
			return;
		}
		ClientState.Remote r = ClientState.remote.get(entity.getId());
		if (r == null) {
			INFO.remove(state);
			return;
		}
		boolean local = entity == mc.player;
		PoseData pose = local ? ClientState.localPose : r.pose;
		float blend = Mth.lerp(partial, r.prevBlend, r.blend);
		float glide = Mth.lerp(partial, r.prevGlide, r.glide);
		float shootAge = r.age - r.shotTick + partial;
		boolean shooting = shootAge >= 0 && shootAge < 7;
		int anchors = (pose.anchor[0] != null ? 1 : 0) | (pose.anchor[1] != null ? 2 : 0);
		float landing = local ? MotionController.landingTicks() / 12.0F : 0.0F;
		if (blend < 0.02F && glide < 0.02F && pose.trick < 0.0F && anchors == 0 && !shooting && landing <= 0.0F) {
			INFO.remove(state);
			return;
		}
		Info i = new Info();
		i.blend = blend;
		i.normal = r.prevNormal.lerp(r.normal, partial);
		i.head = r.prevHead.lerp(r.head, partial);
		i.glide = glide;
		i.trick = pose.trick;
		i.anchors = anchors;
		i.wallOrCeiling = blend > 0.3F;
		i.ceiling = i.normal.y < -0.5;
		i.shoot = shooting ? 1.0F - shootAge / 7.0F : 0.0F;
		i.shootLeft = r.shotLeft;
		i.speed = (float) r.speed;
		i.landing = landing;
		i.age = state.ageInTicks;
		INFO.put(state, i);
	}

	public static Info info(AvatarRenderState state) {
		return INFO.get(state);
	}

	// ------------------------------------------------------------------ body orientation

	public static void applyRotation(AvatarRenderState state, PoseStack stack, float bodyRot) {
		Info i = INFO.get(state);
		if (i == null || (i.blend < 0.02F && i.glide < 0.02F && i.trick < 0.0F)) {
			return;
		}
		Quaternionf total = new Quaternionf();
		float shiftX = 0;
		float shiftY = 0;
		float shiftZ = 0;

		double yaw = Math.toRadians(bodyRot);
		V3 front = new V3(-Math.sin(yaw), 0, Math.cos(yaw));
		V3 up = V3.UP;
		V3 right = front.cross(up).normalize();

		if (i.blend >= 0.02F) {
			V3 n = i.normal.lengthSqr() < 1.0E-6 ? V3.UP : i.normal.normalize();
			V3 h = i.head.sub(n.scale(i.head.dot(n)));
			h = h.lengthSqr() < 1.0E-6 ? (Math.abs(n.y) > 0.9 ? front : V3.UP) : h.normalize();
			V3 bellyDir = n.scale(-1);
			V3 r2 = bellyDir.cross(h).normalize();
			Matrix3f d = new Matrix3f(
				(float) r2.x, (float) r2.y, (float) r2.z,
				(float) h.x, (float) h.y, (float) h.z,
				(float) bellyDir.x, (float) bellyDir.y, (float) bellyDir.z);
			Matrix3f v = new Matrix3f(
				(float) right.x, (float) right.y, (float) right.z,
				(float) up.x, (float) up.y, (float) up.z,
				(float) front.x, (float) front.y, (float) front.z);
			Matrix3f q = new Matrix3f(d).mul(v.transpose());
			Quaternionf qc = q.getNormalizedRotation(new Quaternionf());
			Quaternionf crawl = new Quaternionf().slerp(qc, Mth.clamp(i.blend, 0.0F, 1.0F));
			total.mul(crawl);
			float dist = (i.ceiling ? 0.76F : 0.16F) * i.blend;
			shiftX = (float) (bellyDir.x * dist);
			shiftY = (float) (bellyDir.y * dist);
			shiftZ = (float) (bellyDir.z * dist);
		}
		if (i.glide >= 0.02F) {
			total.mul(new Quaternionf().setAngleAxis((float) Math.toRadians(70.0 * i.glide), (float) -right.x, 0.0F, (float) -right.z));
		}
		if (i.trick >= 0.0F) {
			total.mul(new Quaternionf().setAngleAxis((float) (Math.PI * 2.0 * i.trick), (float) -right.x, 0.0F, (float) -right.z));
		}

		stack.translate(shiftX, shiftY, shiftZ);
		stack.translate(0.0F, 0.9F, 0.0F);
		stack.mulPose(total);
		stack.translate(0.0F, -0.9F, 0.0F);
	}

	// ------------------------------------------------------------------ limbs

	public static void applyLimbs(PlayerModel model, AvatarRenderState state) {
		Info i = INFO.get(state);
		if (i == null) {
			return;
		}
		float t = i.age * 0.5F;
		float amp = Mth.clamp(i.speed * 7.0F, 0.0F, 1.0F);

		if (i.glide > 0.3F) {
			model.rightArm.xRot = 0.0F;
			model.leftArm.xRot = 0.0F;
			model.rightArm.zRot = 1.35F;
			model.leftArm.zRot = -1.35F;
			model.rightLeg.xRot = 0.2F;
			model.leftLeg.xRot = 0.2F;
			model.rightLeg.zRot = 0.12F;
			model.leftLeg.zRot = -0.12F;
		}
		if (i.wallOrCeiling) {
			float s = Mth.sin(t) * amp;
			model.rightArm.xRot = -2.85F + 0.32F * s;
			model.leftArm.xRot = -2.85F - 0.32F * s;
			model.rightArm.zRot = -0.30F;
			model.leftArm.zRot = 0.30F;
			model.rightLeg.xRot = -0.18F - 0.35F * s;
			model.leftLeg.xRot = -0.18F + 0.35F * s;
			model.rightLeg.zRot = 0.32F;
			model.leftLeg.zRot = -0.32F;
			model.head.xRot = -1.0F;
		} else if (i.landing > 0.0F) {
			model.rightArm.xRot = 0.5F * i.landing;
			model.leftArm.xRot = 0.5F * i.landing;
			model.rightArm.zRot = 0.9F * i.landing;
			model.leftArm.zRot = -0.9F * i.landing;
		}
		// the arm that holds a web reaches towards where the web went
		if (i.anchors != 0 && !i.wallOrCeiling) {
			if ((i.anchors & 1) != 0) {
				model.leftArm.xRot = -2.35F;
				model.leftArm.zRot = -0.15F;
			}
			if ((i.anchors & 2) != 0) {
				model.rightArm.xRot = -2.35F;
				model.rightArm.zRot = 0.15F;
			}
		}
		if (i.shoot > 0.0F && !i.wallOrCeiling) {
			float pitch = state.xRot * 0.0174533F;
			if (i.shootLeft) {
				model.leftArm.xRot = -1.45F + pitch;
				model.leftArm.zRot = -0.05F;
			} else {
				model.rightArm.xRot = -1.45F + pitch;
				model.rightArm.zRot = 0.05F;
			}
		}
	}

	public static void clear() {
		INFO.clear();
	}

	/** True when the given motion mode makes the model lie flat (used by tests and tools). */
	public static boolean isFlat(int motion) {
		return motion == MotionMode.WALL || motion == MotionMode.CEILING;
	}
}
