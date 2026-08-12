package com.mamaika.noabuse.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Все методы ниже сверены с официальными Yarn-маппингами на билде проекта
 * (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит 9672e1f) —
 * не по памяти:
 *
 *   LivingEntity#blockedByShield(DamageSource): boolean   (method_6061)
 *   DamageSource#getSource(): Entity                      (method_5526)
 *   Entity#getDamageSources(): DamageSources               (method_48923)
 *   DamageSources#onFire(): DamageSource                   (method_48813)
 *   LivingEntity#getMaxHealth(): float                     (method_6063)
 *   LivingEntity#tickMovement(): void                      (method_6007)
 *
 * ВАЖНО: LivingEntity НЕ переопределяет tick()/baseTick() — оба объявлены
 * только в Entity. Инжект в "tick" на @Mixin(LivingEntity.class) упал бы
 * с "Unable to locate target method", как в своё время было с
 * EyeOfEnderMixin. Реальный per-tick метод именно в LivingEntity —
 * tickMovement(), на него и вешаемся.
 *
 * Ваниль: и ифрит (SmallFireballEntity), и гаст (FireballEntity) шлют урон
 * с одним и тем же DamageTypes.FIREBALL — по типу их не различить.
 * Различаем по точному классу объекта-снаряда, который лежит в
 * DamageSource#getSource() (см. DamageSources#fireball(AbstractFireballEntity
 * source, Entity attacker) — оба snarяда наследуют AbstractFireballEntity,
 * но сами классы разные и не наследуют друг друга).
 *
 * Наша версия: если блок щитом сработал (blockedByShield == true) и
 * источник — фаербол ифрита или гаста, вешаем счётчик кастомного DoT:
 * 1% от maxHealth в секунду, 3 сек для ифрита / 10 сек для гаста.
 *
 * Сознательно НЕ вызываем setOnFireFor() — ванильный тик горения бьёт тем
 * же DamageSources#onFire(), которым бьём мы, и даёт фиксированный урон
 * (обычно 1 хп/сек), который на 20 хп — это уже 5%/сек, то есть полностью
 * забивает наш задуманный 1%/сек. Если нужен визуальный поджиг поверх —
 * это отдельная доработка (плюс подавление ванильного тика урона).
 */
@Mixin(LivingEntity.class)
public abstract class ShieldFireMixin {

    @Unique
    private int noabuse$burnSecondsRemaining = 0;

    @Unique
    private int noabuse$burnTickCounter = 0;

    @Unique
    private float noabuse$burnDamagePerSecond = 0f;

    @Inject(method = "blockedByShield", at = @At("RETURN"))
    private void noabuse$onShieldBlock(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return; // блока не было — не наш случай
        }

        Entity direct = source.getSource();
        if (direct == null) {
            return;
        }

        int seconds;
        if (direct.getClass() == SmallFireballEntity.class) {
            seconds = 3; // ифрит
        } else if (direct.getClass() == FireballEntity.class) {
            seconds = 10; // гаст
        } else {
            return; // заблокировали что-то другое (стрела, меч и т.д.) — не трогаем
        }

        LivingEntity self = (LivingEntity) (Object) this;

        noabuse$burnSecondsRemaining = seconds;
        noabuse$burnTickCounter = 0;
        noabuse$burnDamagePerSecond = self.getMaxHealth() * 0.01f;
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void noabuse$onTickMovement(CallbackInfo ci) {
        if (noabuse$burnSecondsRemaining <= 0) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;

        // считаем урон только на сервере — на клиенте это привело бы
        // к бессмысленным вызовам damage() без сетевой синхронизации
        if (self.getWorld().isClient) {
            return;
        }

        noabuse$burnTickCounter++;
        if (noabuse$burnTickCounter < 20) {
            return; // ждём полную секунду (20 тиков)
        }

        noabuse$burnTickCounter = 0;
        noabuse$burnSecondsRemaining--;
        self.damage(self.getDamageSources().onFire(), noabuse$burnDamagePerSecond);
    }
}
