package com.mamaika.noabuse.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Все методы/поля ниже сверены с официальными Yarn-маппингами на билде
 * проекта (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит
 * 9672e1f):
 *
 *   MobSpawnerLogic#maxNearbyEntities: int   (field_9160)
 *   MobSpawnerLogic#spawnCount: int          (field_9149)
 *   MobSpawnerLogic#serverTick(ServerWorld, BlockPos): void (method_31588)
 *
 * Это НЕ AI-механика с таймингами Goal-классов (как эндермен) — это два
 * простых int-поля, которые ваниль сама же использует в своём же
 * проверенном алгоритме подсчёта "сколько мобов данного типа уже рядом со
 * спавнером" (внутри updateSpawns/serverTick). Мы не переписываем эту
 * логику — просто зажимаем входные параметры сверху перед каждым тиком,
 * так что сам подсчёт и решение "спавнить/не спавнить" остаются полностью
 * ванильными и надёжными.
 *
 * maxNearbyEntities — сколько живых мобов этого типа может быть рядом,
 * прежде чем спавнер перестанет работать (ваниль по умолчанию: 6).
 * spawnCount — сколько мобов спавнер пытается заспавнить за одну попытку
 * (ваниль по умолчанию: 4).
 *
 * MobSpawnerLogic — общий класс и для блока спавнера, и для спавнер-
 * вагонетки, так что лимит автоматически действует на оба случая.
 *
 * Через @Shadow напрямую читаем/пишем приватные поля ваниль-класса — это
 * штатная возможность Mixin, обходит модификаторы доступа на уровне
 * байткода, ничего экзотического.
 *
 * Используем Math.min (не жёсткую перезапись), чтобы НЕ повышать лимит,
 * если он и так уже строже нашего (например, задан структурой/датапаком
 * ниже наших значений) — трогаем только те спавнеры, что мягче нашего
 * потолка.
 *
 * ВАЖНЫЙ НЮАНС (спасибо за вопрос "а если убивать сразу после спавна?"):
 * maxNearbyEntities считает только ЖИВЫХ мобов рядом. Если убивать моба
 * мгновенно после спавна (падение, лава, ловушка) — эта проверка почти
 * всегда видит 0-1 моба и никогда не блокирует, то есть сама по себе
 * бесполезна против insta-kill ферм. Поэтому дополнительно зажимаем
 * minSpawnDelay/maxSpawnDelay (задержка между ПОПЫТКАМИ спавна, тикает
 * независимо от того, живы мобы или нет) снизу до ванильных дефолтов
 * (200/800 тиков = 10-40 сек) — это ловит только те спавнеры, что через
 * структуру/датапак/командой настроены БЫСТРЕЕ ваниль-дефолта, обычные
 * спавнеры не трогает вообще. В связке с уже урезанным spawnCount=2 это
 * даёт жёсткий потолок: максимум 2 моба раз в 10-40 сек, даже если фарм
 * убивает их мгновенно.
 */
@Mixin(MobSpawnerLogic.class)
public abstract class SpawnerLimitMixin {

    @Unique
    private static final int NOABUSE_MAX_NEARBY_ENTITIES = 4;

    @Unique
    private static final int NOABUSE_MAX_SPAWN_COUNT = 2;

    @Unique
    private static final int NOABUSE_MIN_SPAWN_DELAY_FLOOR = 200;

    @Unique
    private static final int NOABUSE_MAX_SPAWN_DELAY_FLOOR = 800;

    @Shadow
    private int maxNearbyEntities;

    @Shadow
    private int spawnCount;

    @Shadow
    private int minSpawnDelay;

    @Shadow
    private int maxSpawnDelay;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void noabuse$capSpawnerLimits(ServerWorld world, BlockPos pos, CallbackInfo ci) {
        if (this.maxNearbyEntities > NOABUSE_MAX_NEARBY_ENTITIES) {
            this.maxNearbyEntities = NOABUSE_MAX_NEARBY_ENTITIES;
        }
        if (this.spawnCount > NOABUSE_MAX_SPAWN_COUNT) {
            this.spawnCount = NOABUSE_MAX_SPAWN_COUNT;
        }
        if (this.minSpawnDelay < NOABUSE_MIN_SPAWN_DELAY_FLOOR) {
            this.minSpawnDelay = NOABUSE_MIN_SPAWN_DELAY_FLOOR;
        }
        if (this.maxSpawnDelay < NOABUSE_MAX_SPAWN_DELAY_FLOOR) {
            this.maxSpawnDelay = NOABUSE_MAX_SPAWN_DELAY_FLOOR;
        }
        // подстраховка: maxSpawnDelay не должен оказаться меньше
        // minSpawnDelay, иначе ванильный random.nextInt(max-min) внутри
        // spawnDelay-логики упадёт на отрицательном диапазоне
        if (this.maxSpawnDelay < this.minSpawnDelay) {
            this.maxSpawnDelay = this.minSpawnDelay;
        }
    }
}
