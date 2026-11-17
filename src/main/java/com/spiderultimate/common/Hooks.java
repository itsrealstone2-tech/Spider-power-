package com.spiderultimate.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

/** Lets common code (items) call into client code without referencing client classes. */
public final class Hooks {
	/** Set by the client initializer; called on the client when the Web Shooter is right-clicked. */
	public static volatile BiConsumer<Player, InteractionHand> webShooterUse = (p, h) -> {
	};

	private Hooks() {
	}
}
