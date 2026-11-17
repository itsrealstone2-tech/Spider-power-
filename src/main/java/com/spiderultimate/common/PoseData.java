package com.spiderultimate.common;

import com.spiderultimate.physics.V3;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Cosmetic snapshot of one player's superhero movement. The owning client produces it every tick while active,
 * the server relays it (together with the anchors it validated itself) to everybody who can see the player.
 */
public final class PoseData {
	public int motion = MotionMode.NONE;
	public int flags;
	public float vx, vy, vz;
	/** Surface normal while crawling (points from the surface towards the player). */
	public float nx, ny = 1.0F, nz;
	/** Direction the head points to while crawling. */
	public float hx, hy = 1.0F, hz;
	/** Air trick progress 0..1, negative when no trick is running. */
	public float trick = -1.0F;
	/** Rope anchors by slot: slot 0 is the left hand, slot 1 the right hand. Null when that hand has no web. */
	public final V3[] anchor = new V3[2];

	public boolean active() {
		return motion != MotionMode.NONE || flags != 0 || anchor[0] != null || anchor[1] != null || trick >= 0.0F;
	}

	public V3 velocity() {
		return new V3(vx, vy, vz);
	}

	public V3 normal() {
		return new V3(nx, ny, nz);
	}

	public V3 head() {
		return new V3(hx, hy, hz);
	}

	public PoseData copy() {
		PoseData p = new PoseData();
		p.motion = motion;
		p.flags = flags;
		p.vx = vx;
		p.vy = vy;
		p.vz = vz;
		p.nx = nx;
		p.ny = ny;
		p.nz = nz;
		p.hx = hx;
		p.hy = hy;
		p.hz = hz;
		p.trick = trick;
		p.anchor[0] = anchor[0];
		p.anchor[1] = anchor[1];
		return p;
	}

	public boolean sameVisual(PoseData o) {
		return motion == o.motion && flags == o.flags && vx == o.vx && vy == o.vy && vz == o.vz
			&& nx == o.nx && ny == o.ny && nz == o.nz && hx == o.hx && hy == o.hy && hz == o.hz
			&& trick == o.trick && java.util.Objects.equals(anchor[0], o.anchor[0]) && java.util.Objects.equals(anchor[1], o.anchor[1]);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeByte(motion);
		buf.writeByte(flags);
		buf.writeFloat(vx);
		buf.writeFloat(vy);
		buf.writeFloat(vz);
		buf.writeFloat(nx);
		buf.writeFloat(ny);
		buf.writeFloat(nz);
		buf.writeFloat(hx);
		buf.writeFloat(hy);
		buf.writeFloat(hz);
		buf.writeFloat(trick);
		buf.writeByte((anchor[0] != null ? 1 : 0) | (anchor[1] != null ? 2 : 0));
		for (V3 a : anchor) {
			if (a != null) {
				buf.writeDouble(a.x);
				buf.writeDouble(a.y);
				buf.writeDouble(a.z);
			}
		}
	}

	public static PoseData read(FriendlyByteBuf buf) {
		PoseData p = new PoseData();
		p.motion = buf.readByte();
		p.flags = buf.readByte();
		p.vx = sane(buf.readFloat());
		p.vy = sane(buf.readFloat());
		p.vz = sane(buf.readFloat());
		p.nx = sane(buf.readFloat());
		p.ny = sane(buf.readFloat());
		p.nz = sane(buf.readFloat());
		p.hx = sane(buf.readFloat());
		p.hy = sane(buf.readFloat());
		p.hz = sane(buf.readFloat());
		p.trick = sane(buf.readFloat());
		int mask = buf.readByte();
		for (int i = 0; i < 2; i++) {
			if ((mask & (1 << i)) != 0) {
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				p.anchor[i] = Double.isFinite(x + y + z) ? new V3(x, y, z) : null;
			}
		}
		return p;
	}

	private static float sane(float f) {
		return Float.isFinite(f) ? f : 0.0F;
	}
}
