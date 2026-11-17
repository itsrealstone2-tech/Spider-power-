package com.spiderultimate.server;

import com.spiderultimate.common.Ability;
import com.spiderultimate.common.PoseData;
import com.spiderultimate.physics.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Everything the server remembers about one player's superhero state. */
public final class PlayerData {
	/** Index of the left hand web cooldown in {@link #cooldowns}. */
	public static final int CD_LEFT = 0;
	public static final int CD_RIGHT = 1;
	/** Index of the first ability cooldown; ability i uses CD_ABILITY + i. */
	public static final int CD_ABILITY = 2;
	/** Remaining ticks of the web shield (sent to the owner in the same array). */
	public static final int CD_SHIELD = CD_ABILITY + Ability.values().length;
	public static final int CD_COUNT = CD_SHIELD + 1;

	public double energy;
	public final int[] cooldowns = new int[CD_COUNT];
	public final V3[] anchors = new V3[2];
	public final BlockPos[] anchorBlocks = new BlockPos[2];
	public double shieldHealth;
	public int reflexCooldown;
	public PoseData pose = new PoseData();
	public boolean lastPoseActive;
	public int lastPoseTick = -1000;
	public int lastStatsTick = -1000;
	public float lastSentEnergy = -1.0F;
	public int lastSentShield = -1;
	public int lastSentCooldownSum = -1;
	public boolean initialised;
	public ResourceKey<Level> dimension;

	public boolean hasAnchor() {
		return anchors[0] != null || anchors[1] != null;
	}

	public void clearAnchor(int slot) {
		anchors[slot] = null;
		anchorBlocks[slot] = null;
	}

	public int anchorMask() {
		return (anchors[0] != null ? 1 : 0) | (anchors[1] != null ? 2 : 0);
	}
}
