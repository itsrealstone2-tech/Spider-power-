package com.spiderultimate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All tunables of the mod. Stored as JSON in {@code config/spiderultimate.json} and shared by the client
 * (physics feel, HUD, effects) and the server (validation, costs, cooldowns).
 */
public final class SpiderConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("spiderultimate");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SpiderConfig instance = new SpiderConfig();

	/** One special ability: can be switched off, has a cooldown and an energy cost. */
	public static final class AbilityCfg {
		public boolean enabled = true;
		public int cooldownTicks = 60;
		public double energyCost = 10;

		public AbilityCfg() {
		}

		public AbilityCfg(int cooldownTicks, double energyCost) {
			this.cooldownTicks = cooldownTicks;
			this.energyCost = energyCost;
		}
	}

	// ---------------------------------------------------------------- general
	/** If true the powers and the web shooter only work with a Web Shooter in a hand or the Spider Suit chestplate worn. */
	public boolean requireGear = true;
	public boolean creativeInfiniteEnergy = true;

	// ---------------------------------------------------------------- web shooter
	public double webRange = 48.0;
	/** Visual speed of the web projectile in blocks per tick; the rope attaches when the web arrives. */
	public double webProjectileSpeed = 5.0;
	public int webCooldownTicks = 6;
	public int rapidCooldownTicks = 3;
	/** How long trap / bomb cobwebs last. */
	public int webDurationTicks = 240;
	public double bombRadius = 4.0;
	public boolean autoAim = true;
	public boolean alternateHands = true;
	/** Allow shooting while airborne or swinging. */
	public boolean midairShooting = true;
	/** Allow webs to pull other players (PvP) in Pull mode. */
	public boolean pullPlayers = false;

	public double maxEnergy = 100.0;
	public double energyRegenPerTick = 0.30;
	public double costSwing = 3.0;
	public double costRapid = 1.2;
	public double costZip = 5.0;
	public double costPull = 6.0;
	public double costTrap = 8.0;
	public double costBomb = 16.0;
	public double costDual = 5.0;

	// ---------------------------------------------------------------- swinging
	/** Multiplies steering and pumping while swinging. */
	public double swingStrength = 1.0;
	/** Hard-ish speed cap in blocks per tick (1.0 = 20 blocks per second). */
	public double maxSwingSpeed = 1.6;
	public double swingDrag = 0.9975;
	public double ropeMinLength = 2.5;
	public double reelSpeed = 0.35;
	/** true: press the swing key once to attach and again to release. false: hold the key to swing. */
	public boolean swingToggle = true;
	/** Upward kick when a swing is released with the jump key. */
	public double releaseJumpBoost = 0.28;
	public int maxAnchors = 2;
	/** Server rejects swings that attach farther than webRange + this. */
	public double serverRangeSlack = 6.0;
	/** Server releases webs if the player is faster than this many blocks per tick. */
	public double serverSpeedLimit = 4.0;

	// ---------------------------------------------------------------- powers
	/** Multiplier of the super jump (1.0 = about six blocks high). */
	public double jumpStrength = 1.0;
	public boolean superJump = true;
	/** Multiplier of the enhanced walking / sprinting speed (0 disables it). */
	public double superSpeed = 1.0;
	public boolean fallProtection = true;
	public boolean airControl = true;
	public boolean wallJump = true;
	public boolean webImpactLanding = true;
	public boolean webWings = true;
	public double wingsMaxSpeed = 2.4;
	public double wingsEnergyPerTick = 0.12;
	public boolean reflexes = true;
	public double reflexDodgeChance = 0.35;
	public boolean wallCrawl = true;
	public boolean ceilingCrawl = true;
	public double crawlSpeed = 0.17;
	/** Attach to walls automatically while crawl mode is on. */
	public boolean autoCrawl = true;
	public double wallJumpStrength = 1.0;

	// ---------------------------------------------------------------- spider sense
	public boolean spiderSense = true;
	public double senseRange = 24.0;
	public boolean senseHostiles = true;
	public boolean senseProjectiles = true;
	public boolean senseGlow = true;

	// ---------------------------------------------------------------- special abilities
	public AbilityCfg swingBoost = new AbilityCfg(60, 12);
	public AbilityCfg quickZip = new AbilityCfg(80, 14);
	public AbilityCfg airTrick = new AbilityCfg(40, 4);
	public AbilityCfg webShield = new AbilityCfg(400, 25);
	public AbilityCfg slowMotion = new AbilityCfg(900, 20);
	public AbilityCfg wallLaunch = new AbilityCfg(50, 10);
	public AbilityCfg multiWeb = new AbilityCfg(160, 22);
	public int shieldDurationTicks = 160;
	public double shieldStrength = 20.0;
	public int multiWebTargets = 5;
	public double multiWebRange = 20.0;
	/** Slow motion only works in single player (it slows the whole game). */
	public double slowMotionTickRate = 8.0;
	public double slowMotionSeconds = 3.0;

	// ---------------------------------------------------------------- suit
	public double suitHelmetArmor = 2;
	public double suitChestplateArmor = 5;
	public double suitLeggingsArmor = 4;
	public double suitBootsArmor = 2;
	public double suitToughness = 1.0;

	// ---------------------------------------------------------------- client presentation
	public boolean hudVisible = true;
	public int hudOffsetX = 8;
	public int hudOffsetY = 8;
	/** Particles, landing dust, wing shimmer and other eye candy. */
	public boolean cinematicEffects = true;
	public double ropeThickness = 0.035;
	public boolean cameraFovBoost = true;

	// ================================================================ access

	public static SpiderConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("spiderultimate.json");
	}

	public static void load() {
		Path p = path();
		SpiderConfig loaded = null;
		if (Files.isRegularFile(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(r, SpiderConfig.class);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Could not read {} - using defaults. Fix or delete the file.", p, e);
			}
		}
		instance = loaded != null ? loaded : new SpiderConfig();
		instance.sanitize();
		save();
	}

	public static void save() {
		Path p = path();
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", p, e);
		}
	}

	/** Keeps every value in a range where the physics stays stable and nothing can divide by zero. */
	private void sanitize() {
		webRange = clamp(webRange, 8, 128);
		webProjectileSpeed = clamp(webProjectileSpeed, 1, 20);
		webCooldownTicks = (int) clamp(webCooldownTicks, 0, 200);
		rapidCooldownTicks = (int) clamp(rapidCooldownTicks, 0, 200);
		webDurationTicks = (int) clamp(webDurationTicks, 20, 2400);
		bombRadius = clamp(bombRadius, 1, 12);
		maxEnergy = clamp(maxEnergy, 10, 10000);
		energyRegenPerTick = clamp(energyRegenPerTick, 0, 20);
		swingStrength = clamp(swingStrength, 0.0, 4.0);
		maxSwingSpeed = clamp(maxSwingSpeed, 0.4, 3.5);
		swingDrag = clamp(swingDrag, 0.95, 1.0);
		ropeMinLength = clamp(ropeMinLength, 1.5, 10);
		reelSpeed = clamp(reelSpeed, 0.05, 1.0);
		releaseJumpBoost = clamp(releaseJumpBoost, 0, 1.5);
		maxAnchors = (int) clamp(maxAnchors, 1, 2);
		serverRangeSlack = clamp(serverRangeSlack, 1, 32);
		serverSpeedLimit = clamp(serverSpeedLimit, 1.0, 9.0);
		jumpStrength = clamp(jumpStrength, 0, 4);
		superSpeed = clamp(superSpeed, 0, 4);
		wingsMaxSpeed = clamp(wingsMaxSpeed, 0.5, 3.5);
		wingsEnergyPerTick = clamp(wingsEnergyPerTick, 0, 5);
		reflexDodgeChance = clamp(reflexDodgeChance, 0, 1);
		crawlSpeed = clamp(crawlSpeed, 0.02, 0.5);
		wallJumpStrength = clamp(wallJumpStrength, 0.1, 4);
		senseRange = clamp(senseRange, 4, 96);
		shieldDurationTicks = (int) clamp(shieldDurationTicks, 20, 1200);
		shieldStrength = clamp(shieldStrength, 1, 1000);
		multiWebTargets = (int) clamp(multiWebTargets, 1, 12);
		multiWebRange = clamp(multiWebRange, 4, 64);
		slowMotionTickRate = clamp(slowMotionTickRate, 1, 20);
		slowMotionSeconds = clamp(slowMotionSeconds, 0.5, 20);
		ropeThickness = clamp(ropeThickness, 0.01, 0.2);
		suitHelmetArmor = clamp(suitHelmetArmor, 0, 10);
		suitChestplateArmor = clamp(suitChestplateArmor, 0, 20);
		suitLeggingsArmor = clamp(suitLeggingsArmor, 0, 15);
		suitBootsArmor = clamp(suitBootsArmor, 0, 10);
		suitToughness = clamp(suitToughness, 0, 10);
		if (swingBoost == null) swingBoost = new AbilityCfg(60, 12);
		if (quickZip == null) quickZip = new AbilityCfg(80, 14);
		if (airTrick == null) airTrick = new AbilityCfg(40, 4);
		if (webShield == null) webShield = new AbilityCfg(400, 25);
		if (slowMotion == null) slowMotion = new AbilityCfg(900, 20);
		if (wallLaunch == null) wallLaunch = new AbilityCfg(50, 10);
		if (multiWeb == null) multiWeb = new AbilityCfg(160, 22);
	}

	private static double clamp(double v, double lo, double hi) {
		if (Double.isNaN(v)) {
			return lo;
		}
		return Math.max(lo, Math.min(hi, v));
	}
}
