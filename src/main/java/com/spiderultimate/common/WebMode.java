package com.spiderultimate.common;

import com.spiderultimate.config.SpiderConfig;

import java.util.Locale;

/** The firing modes of the web shooter. */
public enum WebMode {
	/** One web strand; attaching to a block starts a physics swing. */
	SINGLE,
	/** Fast web bullets that slow whatever they hit. */
	RAPID,
	/** The web reels the player towards the anchor. */
	ZIP,
	/** Yanks the target (mob / item) towards the player. */
	PULL,
	/** Entangles a target or a spot in cobweb. */
	TRAP,
	/** A web grenade: bursts on impact and entangles everything nearby. */
	BOMB,
	/** Both hands at once: two ropes, two anchor points. */
	DUAL;

	public WebMode next() {
		WebMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static WebMode byId(int id) {
		WebMode[] all = values();
		return id >= 0 && id < all.length ? all[id] : SINGLE;
	}

	public String langKey() {
		return "spiderultimate.mode." + name().toLowerCase(Locale.ROOT);
	}

	/** True for the modes whose web becomes a rope the player hangs from. */
	public boolean isRopeMode() {
		return this == SINGLE || this == DUAL || this == ZIP;
	}

	public double cost(SpiderConfig c) {
		return switch (this) {
			case SINGLE -> c.costSwing;
			case RAPID -> c.costRapid;
			case ZIP -> c.costZip;
			case PULL -> c.costPull;
			case TRAP -> c.costTrap;
			case BOMB -> c.costBomb;
			case DUAL -> c.costDual;
		};
	}

	public int cooldown(SpiderConfig c) {
		return this == RAPID ? c.rapidCooldownTicks : c.webCooldownTicks;
	}
}
