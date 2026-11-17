package com.spiderultimate.physics;

/** A web rope: a fixed anchor point and a maximum length (the rope only ever pulls, it never pushes). */
public final class Rope {
	public final int id;
	public V3 anchor;
	public double length;

	public Rope(int id, V3 anchor, double length) {
		this.id = id;
		this.anchor = anchor;
		this.length = length;
	}
}
