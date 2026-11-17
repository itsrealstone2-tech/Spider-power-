package com.spiderultimate.common;

import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Decides whether a player may use the web shooter and the spider powers. */
public final class SpiderGear {
	private SpiderGear() {
	}

	public static boolean holdsWebShooter(Player p) {
		return p.getMainHandItem().is(ModItems.WEB_SHOOTER) || p.getOffhandItem().is(ModItems.WEB_SHOOTER);
	}

	public static boolean wearsSuitChest(Player p) {
		return p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SPIDER_SUIT_CHESTPLATE);
	}

	public static boolean wearsFullSuit(Player p) {
		return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SPIDER_SUIT_HELMET)
			&& wearsSuitChest(p)
			&& p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SPIDER_SUIT_LEGGINGS)
			&& p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.SPIDER_SUIT_BOOTS);
	}

	/** True if the player may use the web shooter and the powers at the moment. */
	public static boolean hasGear(Player p) {
		return !SpiderConfig.get().requireGear || holdsWebShooter(p) || wearsSuitChest(p);
	}
}
