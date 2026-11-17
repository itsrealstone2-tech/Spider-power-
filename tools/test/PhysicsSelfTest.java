import com.spiderultimate.physics.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests for the Minecraft-independent physics core.
 * Run:  javac -d out src/main/java/com/spiderultimate/physics/*.java tools/test/PhysicsSelfTest.java && java -cp out PhysicsSelfTest
 */
public class PhysicsSelfTest {
	static int failures = 0;
	static int checks = 0;

	static void check(String name, boolean ok, String detail) {
		checks++;
		if (!ok) {
			failures++;
		}
		System.out.printf("[%s] %s  %s%n", ok ? "PASS" : "FAIL", name, detail);
	}

	public static void main(String[] args) {
		pendulumEnergyAndRopeLength();
		pendulumPeriod();
		speedCapWithPumping();
		momentumAfterRelease();
		twoRopes();
		reelingConservesAngularMomentum();
		zipNoTeleport();
		glideStability();
		crawlMath();
		autoAim();
		System.out.printf("%n%d checks, %d failed%n", checks, failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	static SwingSolver.Params pureGravity() {
		SwingSolver.Params p = new SwingSolver.Params();
		p.drag = 1.0;
		p.controlAccel = 0;
		p.pumpAccel = 0;
		p.maxSpeed = 100;
		return p;
	}

	static void pendulumEnergyAndRopeLength() {
		SwingSolver.Params p = pureGravity();
		V3 anchor = new V3(0, 30, 0);
		double len = 15;
		double a0 = Math.toRadians(60);
		V3 pos = anchor.add(len * Math.sin(a0), -len * Math.cos(a0), 0);
		V3 vel = V3.ZERO;
		List<Rope> ropes = List.of(new Rope(1, anchor, len));
		double e0 = SwingSolver.energy(pos, vel, p.gravity);
		double amplitude = p.gravity * len * (1 - Math.cos(a0));
		double maxDrift = 0, maxViolation = 0, maxStep = 0, maxSpeed = 0;
		for (int t = 0; t < 600; t++) {
			SwingSolver.Result r = SwingSolver.step(pos, vel, ropes, V3.ZERO, false, p);
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
			maxDrift = Math.max(maxDrift, Math.abs(SwingSolver.energy(pos, vel, p.gravity) - e0));
			maxViolation = Math.max(maxViolation, pos.distanceTo(anchor) - len);
			maxStep = Math.max(maxStep, r.displacement.length());
			maxSpeed = Math.max(maxSpeed, vel.length());
		}
		check("pendulum: rope never stretches", maxViolation < 1e-6, String.format("max stretch %.2e blocks", maxViolation));
		check("pendulum: energy conserved over 30 s", maxDrift / amplitude < 0.06,
				String.format("max drift %.2f%% of swing energy", 100 * maxDrift / amplitude));
		double expectedBottom = Math.sqrt(2 * amplitude);
		check("pendulum: bottom speed matches theory", Math.abs(maxSpeed - expectedBottom) / expectedBottom < 0.06,
				String.format("simulated %.3f vs theory %.3f blocks/tick", maxSpeed, expectedBottom));
		check("pendulum: no teleport (per-tick displacement == speed)", maxStep < expectedBottom * 1.06,
				String.format("max step %.3f blocks", maxStep));
	}

	static void pendulumPeriod() {
		SwingSolver.Params p = pureGravity();
		V3 anchor = new V3(0, 30, 0);
		double len = 15;
		double a0 = Math.toRadians(8);
		V3 pos = anchor.add(len * Math.sin(a0), -len * Math.cos(a0), 0);
		V3 vel = V3.ZERO;
		List<Rope> ropes = List.of(new Rope(1, anchor, len));
		List<Double> crossings = new ArrayList<>();
		double prevX = pos.x - anchor.x;
		for (int t = 1; t < 400 && crossings.size() < 5; t++) {
			SwingSolver.Result r = SwingSolver.step(pos, vel, ropes, V3.ZERO, false, p);
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
			double x = pos.x - anchor.x;
			if (prevX > 0 != x > 0) {
				crossings.add(t - 1 + prevX / (prevX - x));
			}
			prevX = x;
		}
		double measured = 2 * (crossings.get(4) - crossings.get(0)) / 4.0; // 4 half periods
		double theory = 2 * Math.PI * Math.sqrt(len / p.gravity) * (1 + a0 * a0 / 16);
		check("pendulum: period matches 2*pi*sqrt(L/g)", Math.abs(measured - theory) / theory < 0.03,
				String.format("measured %.1f ticks vs theory %.1f ticks", measured, theory));
	}

	static void speedCapWithPumping() {
		SwingSolver.Params p = new SwingSolver.Params();
		p.maxSpeed = 1.6;
		p.pumpAccel = 0.05;
		p.controlAccel = 0.03;
		V3 anchor = new V3(0, 30, 0);
		double len = 15;
		V3 pos = anchor.add(0, -len, 0);
		V3 vel = new V3(1.5, 0, 0);
		List<Rope> ropes = List.of(new Rope(1, anchor, len));
		double maxSpeed = 0;
		for (int t = 0; t < 400; t++) {
			V3 wish = vel.horizontal().normalize();
			SwingSolver.Result r = SwingSolver.step(pos, vel, ropes, wish, false, p);
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
			maxSpeed = Math.max(maxSpeed, vel.length());
		}
		check("speed cap holds while pumping hard", maxSpeed <= p.maxSpeed + 0.08,
				String.format("peak %.3f vs cap %.2f blocks/tick", maxSpeed, p.maxSpeed));
	}

	static void momentumAfterRelease() {
		FlightSolver.Params p = new FlightSolver.Params();
		V3 vel = new V3(1.2, 0, 0);
		double startH = vel.horizontalLength();
		for (int t = 0; t < 10; t++) {
			vel = FlightSolver.step(vel, V3.ZERO, p).endVelocity;
		}
		double ratio = vel.horizontalLength() / startH;
		double vanilla = Math.pow(0.91, 10);
		check("release keeps momentum (vs vanilla air drag)", ratio > 0.95,
				String.format("keeps %.0f%% after 10 ticks; vanilla would keep %.0f%%", ratio * 100, vanilla * 100));
	}

	static void twoRopes() {
		SwingSolver.Params p = new SwingSolver.Params();
		List<Rope> ropes = List.of(new Rope(1, new V3(-6, 25, 0), 13), new Rope(2, new V3(6, 25, 0), 13));
		V3 pos = new V3(0, 14, 0);
		V3 vel = new V3(0.4, 0, 0.3);
		double maxViolation = 0;
		for (int t = 0; t < 400; t++) {
			V3 wish = new V3(Math.sin(t * 0.05), 0, Math.cos(t * 0.07));
			SwingSolver.Result r = SwingSolver.step(pos, vel, ropes, wish, false, p);
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
			for (Rope rope : ropes) {
				maxViolation = Math.max(maxViolation, pos.distanceTo(rope.anchor) - rope.length);
			}
		}
		check("two attachment points: both ropes respected", maxViolation < 0.02,
				String.format("max stretch %.4f blocks", maxViolation));
	}

	static void reelingConservesAngularMomentum() {
		SwingSolver.Params p = pureGravity();
		p.gravity = 0.0; // isolate the effect of reeling
		V3 anchor = new V3(0, 30, 0);
		Rope rope = new Rope(1, anchor, 15);
		V3 pos = anchor.add(0, -15, 0);
		V3 vel = new V3(1.0, 0, 0);
		double l0 = rope.length;
		double h0 = 15 * 1.0;
		for (int t = 0; t < 8; t++) {
			SwingSolver.Result r = SwingSolver.step(pos, vel, List.of(rope), V3.ZERO, true, p);
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
		}
		V3 rel = pos.sub(anchor);
		double angMom = rel.cross(vel).length();
		double expectedSpeed = l0 / rope.length; // v * r = constant
		check("reeling in speeds the swing up as L0/L1", Math.abs(vel.length() - expectedSpeed) / expectedSpeed < 0.05,
				String.format("rope %.1f -> %.1f, speed 1.00 -> %.2f (theory %.2f)", l0, rope.length, vel.length(), expectedSpeed));
		check("reeling conserves angular momentum", Math.abs(angMom - h0) / h0 < 0.03,
				String.format("L = %.2f vs start %.2f", angMom, h0));
	}

	static void zipNoTeleport() {
		ZipSolver.Params p = new ZipSolver.Params();
		V3 anchor = new V3(0, 12, 18);
		V3 pos = V3.ZERO;
		V3 vel = V3.ZERO;
		double maxStep = 0;
		int arrivedAt = -1;
		for (int t = 0; t < 200; t++) {
			ZipSolver.Result r = ZipSolver.step(pos, vel, anchor, p);
			if (r.arrived) {
				arrivedAt = t;
				break;
			}
			pos = pos.add(r.displacement);
			vel = r.endVelocity;
			maxStep = Math.max(maxStep, r.displacement.length());
		}
		check("zip arrives", arrivedAt > 0 && arrivedAt < 90, "arrived after " + arrivedAt + " ticks (21.6 blocks away)");
		check("zip never moves faster than its cap (no teleport)", maxStep <= p.maxSpeed + 0.02,
				String.format("max step %.3f blocks/tick", maxStep));
		check("zip stops near the anchor", pos.distanceTo(anchor) <= p.arriveRadius + 1.0,
				String.format("ended %.2f blocks from anchor", pos.distanceTo(anchor)));
	}

	static void glideStability() {
		GlideSolver.Params p = new GlideSolver.Params();
		V3 vel = new V3(0.6, -0.2, 0);
		double pitch = Math.toRadians(10);
		V3 look = new V3(Math.cos(pitch), -Math.sin(pitch), 0);
		double maxSpeed = 0, minVy = 0;
		boolean finite = true;
		for (int t = 0; t < 300; t++) {
			GlideSolver.Result r = GlideSolver.step(vel, look, p);
			vel = r.endVelocity;
			finite &= vel.isFinite();
			maxSpeed = Math.max(maxSpeed, vel.length());
			minVy = Math.min(minVy, vel.y);
		}
		check("glide: stable, finite and capped", finite && maxSpeed <= p.maxSpeed + 0.1 && minVy > -3.5,
				String.format("peak speed %.2f, worst sink %.2f blocks/tick", maxSpeed, minVy));
		// a steep nose-up must convert speed into height
		V3 v2 = new V3(1.2, 0, 0);
		double up = Math.toRadians(-35);
		V3 lookUp = new V3(Math.cos(up), -Math.sin(up), 0);
		double maxVy = 0;
		for (int t = 0; t < 8; t++) {
			v2 = GlideSolver.step(v2, lookUp, p).endVelocity;
			maxVy = Math.max(maxVy, v2.y);
		}
		check("glide: pulling up trades speed for height", maxVy > 0.1, String.format("peak climb %.2f blocks/tick", maxVy));
	}

	static void crawlMath() {
		V3 wall = new V3(0, 0, 1); // wall to the north, player on its +z side
		V3 up = CrawlMath.surfaceVelocity(wall, 0, 1, 0, 0.2);
		check("crawl: forward climbs the wall", Math.abs(up.y - 0.2) < 1e-9 && Math.abs(up.x) < 1e-9, up.toString());
		V3 ceilRight = CrawlMath.surfaceVelocity(new V3(0, -1, 0), 0, 0, 1, 0.2);
		check("crawl: ceiling strafe is the camera's right (west when facing south)", Math.abs(ceilRight.x + 0.2) < 1e-9, ceilRight.toString());
		V3 right = CrawlMath.surfaceVelocity(wall, 0, 0, 1, 0.2);
		check("crawl: strafe is the viewer's right", Math.abs(right.x - 0.2) < 1e-9 && Math.abs(right.y) < 1e-9, right.toString());
		V3 diag = CrawlMath.surfaceVelocity(wall, 0, 1, 1, 0.2);
		check("crawl: diagonal is not faster", diag.length() <= 0.2 + 1e-9, String.format("%.3f", diag.length()));
		V3 ceil = new V3(0, -1, 0);
		V3 cf = CrawlMath.surfaceVelocity(ceil, 0, 1, 0, 0.2);
		check("crawl: ceiling forward follows the camera (+z at yaw 0)", cf.z > 0.19 && Math.abs(cf.y) < 1e-9, cf.toString());
		V3 jump = CrawlMath.wallJumpVelocity(wall, new V3(0, 0, -1), 1.0);
		check("wall jump leaves the wall and goes up", jump.dot(wall) > 0 && jump.y > 0, jump.toString());
		V3 launch = CrawlMath.wallLaunchVelocity(wall, new V3(0, 0, -1), 1.5);
		check("wall launch never drives into the wall", launch.dot(wall) >= -1e-9, launch.toString());
		check("wall launch has requested speed", Math.abs(launch.length() - 1.5) < 1e-9, String.format("%.3f", launch.length()));
	}

	static void autoAim() {
		V3 look = new V3(0, 0, 1);
		List<V3> c = AutoAim.candidates(look);
		boolean unit = c.stream().allMatch(v -> Math.abs(v.length() - 1) < 1e-9);
		check("auto aim: fan of rays", c.size() == 1 + 4 * 7 && unit && c.get(0).distanceTo(look) < 1e-9, c.size() + " rays");
		V3 eye = new V3(0, 64, 0);
		double ahead = AutoAim.score(eye, new V3(0, 80, 20), look, 40);
		double behind = AutoAim.score(eye, new V3(0, 80, -20), look, 40);
		double low = AutoAim.score(eye, new V3(0, 64.5, 20), look, 40);
		check("auto aim: prefers high points ahead", ahead > behind && ahead > low && behind < -1e8 && low < -1e8,
				String.format("ahead %.1f, behind %.0f, low %.0f", ahead, behind, low));
	}
}
