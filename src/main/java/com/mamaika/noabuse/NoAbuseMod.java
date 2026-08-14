package com.mamaika.noabuse;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoAbuseMod implements ModInitializer {
    public static final String MOD_ID = "noabuse";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // МЕТКА СБОРКИ — меняется при каждой значимой правке, чтобы по
        // одной строке в логе сразу быть уверенным, какая версия кода
        // реально загружена, без гадания по датам файлов/папок.
        LOGGER.info("[NoAbuse] Хардкорный ноабуз-мод загружен (build-tag: DEBUG-2026-08-14-A)");
    }
}
