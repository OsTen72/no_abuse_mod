package com.mamaika.noabuse;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoAbuseMod implements ModInitializer {
    public static final String MOD_ID = "noabuse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[NoAbuse] Хардкорный ноабуз-мод загружен");
    }
}
