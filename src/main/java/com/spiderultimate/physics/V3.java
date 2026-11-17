package com.spiderultimate.physics;

/**
 * Tiny immutable 3D vector used by the Minecraft-independent physics core.
 * Units used everywhere in this package: blocks and ticks (1 tick = 1/20 s).
 */
public final class V3 {
	public static final V3 ZERO = new V3(0, 0, 0);
	public static final V3 UP = new V3(0, 1, 0);

	public final double x;
	public final double y;
	public final double z;

	public V3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 add(double dx, double dy, double dz) {
		return new V3(x + dx, y + dy, z + dz);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 scale(double s) {
		return new V3(x * s, y * s, z * s);
	}

	public double dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public V3 cross(V3 o) {
		return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public double lengthSqr() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSqr());
	}

	public double horizontalLength() {
		return Math.sqrt(x * x + z * z);
	}

	public double distanceTo(V3 o) {
		return sub(o).length();
	}

	/** Unit vector, or ZERO when the vector is (almost) zero. */
	public V3 normalize() {
		double l = length();
		return l < 1.0E-9 ? ZERO : scale(1.0 / l);
	}

	public V3 horizontal() {
		return new V3(x, 0, z);
	}

	public V3 lerp(V3 o, double t) {
		return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
	}

	/** Shortens the vector to at most {@code max} blocks. */
	public V3 limit(double max) {
		double l = length();
		return l > max && l > 1.0E-9 ? scale(max / l) : this;
	}

	public boolean isFinite() {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
	}

	@Override
	public String toString() {
		return String.format("(%.3f, %.3f, %.3f)", x, y, z);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof V3 v)) {
			return false;
		}
		return Double.compare(x, v.x) == 0 && Double.compare(y, v.y) == 0 && Double.compare(z, v.z) == 0;
	}

	@Override
	public int hashCode() {
		return 31 * (31 * Double.hashCode(x) + Double.hashCode(y)) + Double.hashCode(z);
	}
}
