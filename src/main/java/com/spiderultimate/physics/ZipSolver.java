package com.spiderultimate.physics;

/**
 * Web Zip: the player is reeled toward the anchor with a finite acceleration and speed limit. It is a real
 * velocity integration (no teleporting): the player speeds up, homes in on the anchor and eases in on arrival.
 */
public final class ZipSolver {
	public static final class Params {
		public double accel = 0.16;
		public double maxSpeed = 1.35;
		/** Fraction of the sideways velocity removed every tick (homing). */
		public double homing = 0.18;
		public double arriveRadius = 2.0;
		/** How much of gravity still acts while zipping. */
		public double gravityScale = 0.15;
		public double gravity = 0.08;
	}

	public static final class Result {
		public V3 displacement = V3.ZERO;
		public V3 endVelocity = V3.ZERO;
		public boolean arrived;
	}

	private ZipSolver() {
	}

	public static Result step(V3 pos, V3 vel, V3 anchor, Params p) {
		Result res = new Result();
		V3 to = anchor.sub(pos);
		double dist = to.length();
		if (dist <= p.arriveRadius) {
			res.arrived = true;
			res.endVelocity = vel;
			return res;
		}
		V3 dir = to.scale(1.0 / dist);
		double along = vel.dot(dir);
		V3 side = vel.sub(dir.scale(along)).scale(1.0 - p.homing);
		// ease in: never ask for more speed than we can shed before the arrival radius
		double allowed = Math.min(p.maxSpeed, 0.30 + (dist - p.arriveRadius) * 0.30);
		double s = Math.min(allowed, Math.max(along, 0.0) + p.accel);
		V3 v = dir.scale(s).add(side).add(0, -p.gravity * p.gravityScale, 0);
		v = FlightSolver.softCap(v, p.maxSpeed);
		// never overshoot the anchor in one tick
		double toward = v.dot(dir);
		if (toward > dist - p.arriveRadius * 0.5) {
			v = v.sub(dir.scale(toward - Math.max(0.0, dist - p.arriveRadius * 0.5)));
		}
		res.displacement = v;
		res.endVelocity = v;
		return res;
	}
}
