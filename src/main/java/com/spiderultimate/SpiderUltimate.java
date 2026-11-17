package com.spiderultimate;

import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.net.ModPackets;
import com.spiderultimate.registry.ModItems;
import com.spiderultimate.registry.ModSounds;
import com.spiderultimate.server.SpiderServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: runs on the client (for single player and LAN) and on dedicated servers. */
public class SpiderUltimate implements ModInitializer {
	public static final String MOD_ID = "spiderultimate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SpiderConfig.load();
		ModSounds.init();
		ModItems.init();
		ModPackets.register();
		SpiderServer.init();
		LOGGER.info("Spider Ultimate loaded: web range {} blocks, config at config/{}.json", SpiderConfig.get().webRange, MOD_ID);
	}
}
