package com.spiderultimate.common;

import com.spiderultimate.config.SpiderConfig;

import java.util.Locale;

/** Special abilities the player can select (B) and trigger (V). */
public enum Ability {
	SWING_BOOST,
	QUICK_ZIP,
	AIR_TRICK,
	WEB_SHIELD,
	SLOW_MOTION,
	WALL_LAUNCH,
	MULTI_WEB;

	public Ability next() {
		Ability[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static Ability byId(int id) {
		Ability[] all = values();
		return id >= 0 && id < all.length ? all[id] : SWING_BOOST;
	}

	public String langKey() {
		return "spiderultimate.ability." + name().toLowerCase(Locale.ROOT);
	}

	public SpiderConfig.AbilityCfg cfg(SpiderConfig c) {
		return switch (this) {
			case SWING_BOOST -> c.swingBoost;
			case QUICK_ZIP -> c.quickZip;
			case AIR_TRICK -> c.airTrick;
			case WEB_SHIELD -> c.webShield;
			case SLOW_MOTION -> c.slowMotion;
			case WALL_LAUNCH -> c.wallLaunch;
			case MULTI_WEB -> c.multiWeb;
		};
	}
}
