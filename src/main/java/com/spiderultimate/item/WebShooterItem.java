package com.spiderultimate.item;

import com.spiderultimate.common.Hooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Web Shooter. Right-clicking it fires a web in the current mode (the actual aiming, physics and
 * networking is done by the client controller; the server validates everything).
 */
public class WebShooterItem extends Item {
	public WebShooterItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			Hooks.webShooterUse.accept(player, hand);
		}
		return InteractionResult.CONSUME;
	}
}
