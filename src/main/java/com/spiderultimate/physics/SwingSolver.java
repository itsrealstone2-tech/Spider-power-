package com.spiderultimate.physics;

import java.util.List;

/**
 * Rope-constrained pendulum solver (position based dynamics with sub-stepping).
 *
 * <p>The player is a point mass. Gravity, a tiny air drag and the player's steering input are
 * integrated, and after every sub-step the position is projected back inside every rope's sphere
 * (a rope can pull, never push). The velocity is derived from the constrained motion, so the
 * outward radial velocity vanishes by itself and the tangential velocity (momentum) is kept.
 * Nothing here ever teleports: the returned displacement is always the integrated path of one tick.
 */
public final class SwingSolver {
	public static final class Params {
		/** Blocks / tick^2. Vanilla is 0.08. */
		public double gravity = 0.08;
		/** Velocity multiplier per tick (vanilla air drag is 0.91 / 0.98; a web swing keeps momentum). */
		public double drag = 0.9975;
		/** Steering acceleration from the movement keys. */
		public double controlAccel = 0.010;
		/** Extra acceleration along the current motion while the player pushes that way ("pumping"). */
		public double pumpAccel = 0.012;
		/** Speed cap in blocks / tick. */
		public double maxSpeed = 1.6;
		/** Fraction of the speed above the cap that is removed every tick. */
		public double softCap = 0.8;
		/** Rope shortening per tick while reeling in. */
		public double reelSpeed = 0.35;
		public double minRope = 2.5;
		public int substeps = 4;
	}

	public static final class Result {
		public V3 displacement = V3.ZERO;
		public V3 endVelocity = V3.ZERO;
		public boolean taut;
	}

	private static final double TAUT_EPS = 1.0E-4;

	private SwingSolver() {
	}

	/**
	 * @param pos   reference point of the player (where the rope is attached)
	 * @param vel   velocity entering this tick, blocks / tick
	 * @param ropes active ropes (lengths may be shortened by reeling)
	 * @param wish  horizontal steering direction (length 0..1) or ZERO
	 * @param reel  true to shorten the ropes this tick
	 */
	public static Result step(V3 pos, V3 vel, List<Rope> ropes, V3 wish, boolean reel, Params p) {
		if (reel) {
			for (Rope r : ropes) {
				double newLen = Math.max(p.minRope, r.length - p.reelSpeed);
				if (newLen < r.length) {
					vel = reelVelocity(pos, vel, r.anchor, r.length, newLen);
					r.length = newLen;
				}
			}
		}

		int n = Math.max(1, p.substeps);
		double dt = 1.0 / n;
		double dragSub = Math.pow(p.drag, dt);
		V3 steer = wish.scale(p.controlAccel);
		V3 x = pos;
		V3 v = vel;
		boolean taut = false;

		for (int i = 0; i < n; i++) {
			V3 a = new V3(steer.x, -p.gravity, steer.z);
			v = v.add(a.scale(dt));
			if (p.pumpAccel > 0 && wish.lengthSqr() > 1.0E-6) {
				V3 vh = v.horizontal();
				double sp = vh.length();
				if (sp > 0.05) {
					double align = Math.max(0.0, wish.dot(vh.scale(1.0 / sp)));
					v = v.add(vh.scale(p.pumpAccel * align * dt / sp));
				}
			}
			v = v.scale(dragSub);

			// Tension: a taut rope cancels any velocity that would stretch it.
			V3 xOld = x;
			int count = ropes.size();
			boolean[] wasTaut = new boolean[count];
			V3[] nOld = new V3[count];
			for (int k = 0; k < count; k++) {
				Rope r = ropes.get(k);
				V3 d = x.sub(r.anchor);
				double dist = d.length();
				if (dist > 1.0E-9) {
					nOld[k] = d.scale(1.0 / dist);
					wasTaut[k] = dist >= r.length - TAUT_EPS;
					if (wasTaut[k]) {
						double vr = v.dot(nOld[k]);
						if (vr > 0) {
							v = v.sub(nOld[k].scale(vr));
						}
					}
				}
			}

			x = x.add(v.scale(dt));
			for (int it = 0; it < 4; it++) {
				boolean moved = false;
				for (Rope r : ropes) {
					V3 d = x.sub(r.anchor);
					double dist = d.length();
					if (dist > r.length && dist > 1.0E-9) {
						x = r.anchor.add(d.scale(r.length / dist));
						moved = true;
					}
				}
				if (!moved) {
					break;
				}
			}

			// The rope turned the player: sliding along a taut rope keeps its speed (parallel transport of the
			// velocity), a rope that just snapped taut removes the outward velocity (inelastic jerk).
			for (int k = 0; k < count; k++) {
				Rope r = ropes.get(k);
				V3 d = x.sub(r.anchor);
				double dist = d.length();
				if (dist < r.length - TAUT_EPS || dist < 1.0E-9 || nOld[k] == null) {
					continue;
				}
				taut = true;
				V3 nNew = d.scale(1.0 / dist);
				if (wasTaut[k]) {
					v = rotateTo(v, nOld[k], nNew);
				} else {
					double vr = v.dot(nNew);
					if (vr > 0) {
						v = v.sub(nNew.scale(vr));
					}
				}
			}
		}

		double speed = v.length();
		if (speed > p.maxSpeed) {
			double excess = speed - p.maxSpeed;
			v = v.scale((speed - excess * p.softCap) / speed);
		}

		Result res = new Result();
		res.displacement = x.sub(pos);
		res.endVelocity = v;
		res.taut = taut;
		return res;
	}

	/** Conserves angular momentum when the rope is shortened: the tangential speed grows as the radius shrinks. */
	static V3 reelVelocity(V3 pos, V3 vel, V3 anchor, double oldLen, double newLen) {
		V3 r = pos.sub(anchor);
		double len = r.length();
		if (len < 1.0E-6) {
			return vel;
		}
		V3 rhat = r.scale(1.0 / len);
		double vr = vel.dot(rhat);
		V3 vt = vel.sub(rhat.scale(vr));
		double k = Math.min(1.35, oldLen / newLen);
		return rhat.scale(Math.min(vr, 0.0)).add(vt.scale(k));
	}

	/** Rotates {@code v} by the rotation that turns unit vector {@code from} into unit vector {@code to}. */
	static V3 rotateTo(V3 v, V3 from, V3 to) {
		V3 axis = from.cross(to);
		double s = axis.length();
		double c = from.dot(to);
		if (s < 1.0E-12) {
			return v;
		}
		V3 k = axis.scale(1.0 / s);
		return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1.0 - c)));
	}

	/** Mechanical energy per unit mass in (blocks/tick)^2, used by the self test. */
	public static double energy(V3 pos, V3 vel, double gravity) {
		return 0.5 * vel.lengthSqr() + gravity * pos.y;
	}
}
