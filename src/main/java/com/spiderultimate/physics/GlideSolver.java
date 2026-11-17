package com.spiderultimate.physics;

/**
 * Web wings gliding. This is the well known vanilla elytra model (pitch converts height into speed and back)
 * so it feels familiar, with a configurable lift factor and a little forward thrust so the wings never stall hard.
 */
public final class GlideSolver {
	public static final class Params {
		public double gravity = 0.08;
		/** Vanilla elytra uses 0.75; larger values glide flatter. */
		public double lift = 0.85;
		/** Forward acceleration along the look direction, blocks / tick^2. */
		public double thrust = 0.008;
		public double maxSpeed = 2.4;
	}

	public static final class Result {
		public V3 displacement = V3.ZERO;
		public V3 endVelocity = V3.ZERO;
	}

	private GlideSolver() {
	}

	public static Result step(V3 vel, V3 lookIn, Params p) {
		V3 look = lookIn.normalize();
		Result res = new Result();
		if (look.lengthSqr() < 0.5) {
			res.displacement = vel;
			res.endVelocity = vel;
			return res;
		}
		double f = Math.asin(-Math.max(-1.0, Math.min(1.0, look.y))); // pitch in radians, positive = looking down
		double hLook = Math.sqrt(look.x * look.x + look.z * look.z);
		double hSpeed = vel.horizontalLength();
		double c = Math.cos(f);
		double d5 = c * c;

		double vx = vel.x;
		double vy = vel.y + p.gravity * (-1.0 + d5 * p.lift);
		double vz = vel.z;
		if (vy < 0.0 && hLook > 0.0) {
			double d6 = vy * -0.1 * d5;
			vx += look.x * d6 / hLook;
			vy += d6;
			vz += look.z * d6 / hLook;
		}
		if (f < 0.0 && hLook > 0.0) {
			double d10 = hSpeed * -Math.sin(f) * 0.04;
			vx += -look.x * d10 / hLook;
			vy += d10 * 3.2;
			vz += -look.z * d10 / hLook;
		}
		if (hLook > 0.0) {
			vx += (look.x / hLook * hSpeed - vx) * 0.1;
			vz += (look.z / hLook * hSpeed - vz) * 0.1;
		}
		vx += look.x * p.thrust;
		vy += look.y * p.thrust * 0.5;
		vz += look.z * p.thrust;

		V3 v = new V3(vx * 0.99, vy * 0.98, vz * 0.99);
		v = FlightSolver.softCap(v, p.maxSpeed);
		res.displacement = v;
		res.endVelocity = v;
		return res;
	}
}
