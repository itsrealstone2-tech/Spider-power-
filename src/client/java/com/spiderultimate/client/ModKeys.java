package com.spiderultimate.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Every key of the mod. All of them can be changed in the vanilla Controls menu (category "Spider Ultimate"). */
public final class ModKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath("spiderultimate", "main"));

	// GLFW key codes (kept as numbers on purpose, they never change)
	private static final int KEY_R = 82;
	private static final int KEY_F = 70;
	private static final int KEY_G = 71;
	private static final int KEY_X = 88;
	private static final int KEY_C = 67;
	private static final int KEY_V = 86;
	private static final int KEY_B = 66;
	private static final int KEY_Z = 90;
	private static final int KEY_H = 72;
	private static final int KEY_J = 74;
	private static final int KEY_K = 75;
	private static final int KEY_LEFT_ALT = 342;
	private static final int UNBOUND = -1;

	/** Fires the web shooter in the current mode. */
	public static final KeyMapping SHOOT = key("shoot", KEY_R);
	/** Shoots a swing web (auto aimed) and releases it again. */
	public static final KeyMapping SWING = key("swing", KEY_F);
	public static final KeyMapping SENSE = key("spider_sense", KEY_G);
	public static final KeyMapping WINGS = key("wings", KEY_X);
	public static final KeyMapping MODE = key("change_mode", KEY_C);
	/** Uses the selected special ability. */
	public static final KeyMapping SPECIAL = key("special", KEY_V);
	public static final KeyMapping ABILITY_NEXT = key("next_ability", KEY_B);
	public static final KeyMapping CRAWL = key("toggle_crawl", KEY_LEFT_ALT);
	public static final KeyMapping RELEASE = key("release", KEY_Z);
	/** Hold to shorten the rope and speed up the swing. */
	public static final KeyMapping REEL = key("reel", KEY_J);
	public static final KeyMapping HUD = key("toggle_hud", KEY_H);
	public static final KeyMapping RELOAD_CONFIG = key("reload_config", KEY_K);
	public static final KeyMapping SHOOT_LEFT = key("shoot_left", UNBOUND);
	public static final KeyMapping SHOOT_RIGHT = key("shoot_right", UNBOUND);

	private ModKeys() {
	}

	private static KeyMapping key(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.spiderultimate." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	/** Forces class initialisation (registration of the keys). */
	public static void init() {
	}
}
