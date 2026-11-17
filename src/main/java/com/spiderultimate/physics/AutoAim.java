package com.spiderultimate.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Aim assist for web swinging (important on touch controls, where precise aiming is hard):
 * when the crosshair does not hit anything useful, a fan of rays up and ahead is tried and scored.
 */
public final class AutoAim {
	private static final double[] YAW_OFFSETS = {0, 10, -10, 20, -20, 32, -32};
	private static final double[] ELEVATIONS = {25, 38, 50, 62};

	private AutoAim() {
	}

	/** Candidate ray directions in order of preference (first = exactly where the player looks). */
	public static List<V3> candidates(V3 look) {
		List<V3> out = new ArrayList<>();
		V3 l = look.normalize();
		out.add(l);
		double lookYaw = Math.atan2(-l.x, l.z); // Minecraft yaw convention in radians
		for (double elev : ELEVATIONS) {
			for (double yo : YAW_OFFSETS) {
				double yaw = lookYaw + Math.toRadians(yo);
				double e = Math.toRadians(elev);
				out.add(new V3(-Math.sin(yaw) * Math.cos(e), Math.sin(e), Math.cos(yaw) * Math.cos(e)));
			}
		}
		return out;
	}

	/**
	 * Higher is better. Prefers points ahead, well above the player and at a comfortable distance;
	 * returns a very negative number for points that make no sense as a swing anchor.
	 */
	public static double score(V3 eye, V3 hit, V3 look, double range) {
		V3 d = hit.sub(eye);
		double dist = d.length();
		if (dist < 4.0) {
			return -1.0E9;
		}
		V3 dh = d.horizontal().normalize();
		V3 lh = look.horizontal().normalize();
		double forward = dh.lengthSqr() < 1.0E-6 || lh.lengthSqr() < 1.0E-6 ? 0.0 : dh.dot(lh);
		if (forward < 0.25) {
			return -1.0E9;
		}
		double height = Math.max(-2.0, Math.min(25.0, d.y));
		if (height < 2.0) {
			return -1.0E9;
		}
		return forward * 10.0 + height * 0.4 - Math.abs(dist - range * 0.6) * 0.15;
	}
}
