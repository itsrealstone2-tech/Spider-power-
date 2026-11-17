package com.spiderultimate.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sound events of the mod. The audio files are procedurally generated originals (see assets/spiderultimate/sounds). */
public final class ModSounds {
	public static final SoundEvent WEB_SHOOT = register("web_shoot");
	public static final SoundEvent WEB_HIT = register("web_hit");
	public static final SoundEvent WEB_SWING = register("web_swing");
	public static final SoundEvent WEB_LAND = register("web_land");
	public static final SoundEvent SPIDER_SENSE = register("spider_sense");
	public static final SoundEvent WEB_WINGS = register("web_wings");
	public static final SoundEvent WEB_SHIELD = register("web_shield");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath("spiderultimate", name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class initialisation (and therefore registration). */
	public static void init() {
	}
}
