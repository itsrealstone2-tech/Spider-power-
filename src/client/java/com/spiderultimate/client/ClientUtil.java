package com.spiderultimate.client;

import com.spiderultimate.physics.CrawlMath;
import com.spiderultimate.physics.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Small helpers shared by the client classes. */
public final class ClientUtil {
	private ClientUtil() {
	}

	public static V3 v3(Vec3 v) {
		return new V3(v.x, v.y, v.z);
	}

	public static Vec3 vec(V3 v) {
		return new Vec3(v.x, v.y, v.z);
	}

	/** Where a web leaves the body: roughly the wrist of the given hand. */
	public static Vec3 handPos(Entity e, boolean left, float partial) {
		Vec3 eye = e.getEyePosition(partial);
		Vec3 look = e.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			right = new Vec3(1, 0, 0);
		}
		right = right.normalize();
		double side = left ? -0.38 : 0.38;
		return eye.add(right.scale(side)).add(look.scale(0.4)).add(0, -0.4, 0);
	}

	/** Horizontal steering direction from the movement keys, relative to where the player looks. Length 0..1. */
	public static V3 wish(Minecraft mc, LocalPlayer p) {
		Options o = mc.options;
		double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		double s = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
		if (f == 0 && s == 0) {
			return V3.ZERO;
		}
		V3 d = CrawlMath.forwardFromYaw(p.getYRot()).scale(f).add(CrawlMath.rightFromYaw(p.getYRot()).scale(s));
		double l = d.length();
		return l > 1.0 ? d.scale(1.0 / l) : d;
	}

	public static double forwardInput(Minecraft mc) {
		return (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
	}

	public static double strafeRightInput(Minecraft mc) {
		return (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
	}

	/** Reference point of the player used by the rope physics (upper body). */
	public static V3 bodyRef(LocalPlayer p) {
		return new V3(p.getX(), p.getY() + p.getBbHeight() * 0.62, p.getZ());
	}
}
