package com.spiderultimate.physics;

/**
 * Free flight after a web is released (and any other airborne moment): momentum is preserved with a very
 * low horizontal drag (vanilla would shave 9% of the speed every tick) while the player can still steer.
 */
public final class FlightSolver {
	public static final class Params {
		public double gravity = 0.08;
		public double horizontalDrag = 0.9975;
		public double verticalDrag = 0.985;
		/** Air steering acceleration (blocks / tick^2). */
		public double control = 0.014;
		public double maxSpeed = 2.2;
	}

	public static final class Result {
		public V3 displacement = V3.ZERO;
		public V3 endVelocity = V3.ZERO;
	}

	private FlightSolver() {
	}

	public static Result step(V3 vel, V3 wish, Params p) {
		V3 v = vel.add(wish.x * p.control, -p.gravity, wish.z * p.control);
		v = new V3(v.x * p.horizontalDrag, v.y * p.verticalDrag, v.z * p.horizontalDrag);
		v = softCap(v, p.maxSpeed);
		Result r = new Result();
		r.displacement = v;
		r.endVelocity = v;
		return r;
	}

	static V3 softCap(V3 v, double max) {
		double s = v.length();
		if (s > max && s > 1.0E-9) {
			double excess = s - max;
			return v.scale((s - excess * 0.8) / s);
		}
		return v;
	}
}
