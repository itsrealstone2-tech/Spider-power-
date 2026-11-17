package com.spiderultimate.client;

import com.spiderultimate.common.MotionMode;
import com.spiderultimate.common.SpiderGear;
import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.server.PlayerData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** The heads-up display: mode, ability, energy, cooldowns, status chips, spider sense indicator. */
public final class SpiderHud {
	private SpiderHud() {
	}

	private static String t(String key) {
		return Component.translatable(key).getString();
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		SpiderConfig c = SpiderConfig.get();
		if (p == null || mc.options.hideGui || !SpiderGear.hasGear(p)) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();

		if (ClientState.messageTicks > 0 && !ClientState.message.isEmpty()) {
			int tw = font.width(ClientState.message);
			g.drawString(font, ClientState.message, (w - tw) / 2, h - 62, 0xFFFFD45E);
		}
		senseIndicator(g, mc, p, w, h);
		if (!c.hudVisible) {
			return;
		}

		int x = c.hudOffsetX;
		int y = c.hudOffsetY;
		int pw = 124;
		g.fill(x - 4, y - 4, x + pw, y + 62, 0x88000000);
		g.fill(x - 4, y - 4, x - 2, y + 62, 0xFFD32F2F);

		g.drawString(font, t("spiderultimate.hud.mode") + ": " + t(ClientState.mode.langKey()), x, y, 0xFFFFFFFF);
		g.drawString(font, t("spiderultimate.hud.ability") + ": " + t(ClientState.ability.langKey()), x, y + 11, 0xFFB3E5FC);

		// energy bar
		float frac = ClientState.maxEnergy <= 0 ? 0 : Math.max(0, Math.min(1, ClientState.energy / ClientState.maxEnergy));
		int bx = x;
		int by = y + 24;
		g.fill(bx, by, bx + 110, by + 6, 0xFF263238);
		g.fill(bx, by, bx + (int) (110 * frac), by + 6, frac > 0.25F ? 0xFF4FC3F7 : 0xFFEF5350);

		// ability cooldown bar
		int cd = ClientState.cooldowns[PlayerData.CD_ABILITY + ClientState.ability.ordinal()];
		int maxCd = Math.max(1, ClientState.ability.cfg(c).cooldownTicks);
		float ready = cd <= 0 ? 1.0F : 1.0F - Math.min(1.0F, cd / (float) maxCd);
		g.fill(bx, by + 9, bx + 110, by + 13, 0xFF263238);
		g.fill(bx, by + 9, bx + (int) (110 * ready), by + 13, cd <= 0 ? 0xFF81C784 : 0xFFFFB74D);

		// hand pips
		boolean l = ClientState.cooldowns[PlayerData.CD_LEFT] <= 0;
		boolean r = ClientState.cooldowns[PlayerData.CD_RIGHT] <= 0;
		g.fill(bx, by + 16, bx + 10, by + 20, l ? 0xFF81C784 : 0xFFE57373);
		g.fill(bx + 14, by + 16, bx + 24, by + 20, r ? 0xFF81C784 : 0xFFE57373);
		g.drawString(font, t("spiderultimate.hud.shooter"), bx + 30, by + 14, 0xFFCCCCCC);

		// status chips
		StringBuilder sb = new StringBuilder();
		int m = ClientState.localPose.motion;
		if (m == MotionMode.SWING) {
			sb.append(t("spiderultimate.hud.swinging")).append(' ');
		} else if (m == MotionMode.ZIP) {
			sb.append(t("spiderultimate.hud.zipping")).append(' ');
		} else if (m == MotionMode.GLIDE) {
			sb.append(t("spiderultimate.hud.gliding")).append(' ');
		} else if (m == MotionMode.WALL || m == MotionMode.CEILING) {
			sb.append(t("spiderultimate.hud.crawling")).append(' ');
		}
		if (ClientState.cooldowns[PlayerData.CD_SHIELD] > 0) {
			sb.append(t("spiderultimate.hud.shield")).append(' ');
		}
		g.drawString(font, sb.toString().trim(), bx, by + 24, 0xFFFFFFFF);
		String sense = ClientState.senseOn ? t("spiderultimate.hud.sense_on") : t("spiderultimate.hud.sense_off");
		String crawl = ClientState.crawlMode ? t("spiderultimate.hud.crawl_on") : t("spiderultimate.hud.crawl_off");
		g.drawString(font, sense + "  " + crawl, bx, by + 35, ClientState.senseOn ? 0xFFFFCC80 : 0xFF888888);
	}

	private static void senseIndicator(GuiGraphics g, Minecraft mc, LocalPlayer p, int w, int h) {
		SpiderConfig c = SpiderConfig.get();
		if (!c.spiderSense || !ClientState.senseOn || SpiderSense.threats.isEmpty()) {
			return;
		}
		int cx = w / 2;
		int cy = h / 2;
		double yaw = Math.toRadians(p.getYRot());
		Vec3 pos = p.position();
		for (SpiderSense.Threat th : SpiderSense.threats) {
			double dx = th.pos().x - pos.x;
			double dz = th.pos().z - pos.z;
			// angle of the threat relative to where the player looks (0 = straight ahead)
			double ang = Math.atan2(-dx, dz) - yaw;
			double radius = 34.0 + Math.min(10.0, th.distance() * 0.3);
			int ix = cx + (int) Math.round(Math.sin(ang) * radius);
			int iy = cy - (int) Math.round(Math.cos(ang) * radius);
			int col = th.projectile() ? 0xFFFFEE58 : 0xFFFF5252;
			g.fill(ix - 2, iy - 2, ix + 3, iy + 3, col);
			g.fill(ix - 1, iy - 4, ix + 2, iy - 3, col);
			g.fill(ix - 4, iy - 1, ix - 3, iy + 2, col);
			g.fill(ix + 3, iy - 1, ix + 4, iy + 2, col);
			g.fill(ix - 1, iy + 3, ix + 2, iy + 4, col);
		}
		if (SpiderSense.flash > 0 && c.cinematicEffects) {
			int a = Math.min(120, SpiderSense.flash * 9);
			int col = (a << 24) | 0xFFFFFF;
			g.fill(0, 0, w, 3, col);
			g.fill(0, h - 3, w, h, col);
			g.fill(0, 0, 3, h, col);
			g.fill(w - 3, 0, w, h, col);
		}
	}
}
