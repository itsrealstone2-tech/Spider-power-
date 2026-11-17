package com.spiderultimate.common;

/** What kind of superhero movement a player is currently doing (used for animation and sync). */
public final class MotionMode {
	public static final int NONE = 0;
	public static final int SWING = 1;
	public static final int ZIP = 2;
	public static final int GLIDE = 3;
	public static final int WALL = 4;
	public static final int CEILING = 5;
	/** Free flight after a release (airborne with momentum). */
	public static final int AIR = 6;

	/** PoseData.flags bits. */
	public static final int FLAG_WINGS = 1;
	public static final int FLAG_SENSE = 2;
	public static final int FLAG_SHIELD = 4;
	public static final int FLAG_CRAWL_MODE = 8;

	private MotionMode() {
	}
}
