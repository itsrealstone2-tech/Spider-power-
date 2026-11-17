package com.spiderultimate.physics;

/**
 * Math for sticking to and moving along walls and ceilings.
 * A surface normal points from the surface toward the player.
 */
public final class CrawlMath {
	private CrawlMath() {
	}

	public static boolean isCeiling(V3 n) {
		return n.y < -0.5;
	}

	public static boolean isWall(V3 n) {
		return Math.abs(n.y) <= 0.5;
	}

	/** Forward unit vector for a Minecraft yaw (degrees). */
	public static V3 forwardFromYaw(double yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new V3(-Math.sin(r), 0, Math.cos(r));
	}

	/**
	 * Right-hand unit vector for a Minecraft yaw (degrees). Facing south (yaw 0, +Z) the right hand points west (-X),
	 * which is also the direction opposite to the "strafe left" acceleration used by vanilla.
	 */
	public static V3 rightFromYaw(double yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new V3(-Math.cos(r), 0, -Math.sin(r));
	}

	/**
	 * Movement along a surface.
	 * Walls: forward = up the wall, back = down, strafe = sideways as seen by someone facing the wall.
	 * Ceilings: forward / strafe follow the camera direction.
	 *
	 * @return velocity in blocks / tick, never longer than {@code speed}
	 */
	public static V3 surfaceVelocity(V3 normal, double yawDeg, double forward, double strafe, double speed) {
		V3 dirF;
		V3 dirR;
		if (isCeiling(normal)) {
			dirF = forwardFromYaw(yawDeg);
			dirR = rightFromYaw(yawDeg);
		} else {
			dirF = V3.UP;
			dirR = V3.UP.cross(normal).normalize();
		}
		V3 v = dirF.scale(forward).add(dirR.scale(strafe));
		double l = v.length();
		if (l > 1.0) {
			v = v.scale(1.0 / l);
		}
		return v.scale(speed);
	}

	/** Pushes the player into the surface so collision detection keeps reporting contact. */
	public static V3 stickVelocity(V3 normal, double strength) {
		return normal.scale(-strength);
	}

	/** Wall jump: away from the surface and up, plus a share of where the player is looking. */
	public static V3 wallJumpVelocity(V3 normal, V3 look, double strength) {
		V3 away = normal.scale(0.55);
		V3 up = new V3(0, isCeiling(normal) ? -0.15 : 0.50, 0);
		V3 aim = look.horizontal().normalize().scale(0.18);
		return away.add(up).add(aim).scale(strength);
	}

	/** Wall launch: a strong leap along the look direction, never into the surface. */
	public static V3 wallLaunchVelocity(V3 normal, V3 look, double strength) {
		V3 d = look.normalize();
		double into = d.dot(normal);
		if (into < 0.15) {
			d = d.add(normal.scale(0.15 - into)).normalize();
		}
		return d.scale(strength);
	}

	/** Model orientation angle (degrees) so the head leads the crawl direction on the surface. */
	public static double crawlRollDeg(V3 normal, V3 moveDir, double yawDeg) {
		if (moveDir.lengthSqr() < 1.0E-6) {
			return 0.0;
		}
		V3 d = moveDir.normalize();
		if (isCeiling(normal)) {
			V3 f = forwardFromYaw(yawDeg);
			V3 r = rightFromYaw(yawDeg);
			return Math.toDegrees(Math.atan2(d.dot(r), d.dot(f)));
		}
		V3 right = V3.UP.cross(normal).normalize();
		return Math.toDegrees(Math.atan2(d.dot(right), d.y));
	}
}
