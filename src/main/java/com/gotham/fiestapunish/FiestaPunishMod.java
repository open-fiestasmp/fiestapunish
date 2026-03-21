package com.gotham.fiestapunish;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiestaPunishMod implements ModInitializer {

    public static final String MOD_ID = "fiestapunish";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[FiestaPunish] Loading...");
        FilterConfig.load();
        PunishmentManager.load();
        ChatEventHandler.register();
        FiestaCommands.register();
        LOGGER.info("[FiestaPunish] Ready — {} words, {} phrases loaded.",
                FilterConfig.getWordCount(), FilterConfig.getPhraseCount());
    }
}
