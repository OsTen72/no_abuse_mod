package com.mamaika.noabuse.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
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
 *   LivingEntity#blockedByShield(DamageSource): boolean        (method_6061)
 *   LivingEntity#getActiveItem(): ItemStack                    (method_6030)
 *   LivingEntity#getActiveHand(): Hand                         (method_6058)
 *   LivingEntity#sendToolBreakStatus(Hand): void                (method_20236)
 *   LivingEntity#tickMovement(): void                          (method_6007)
 *   DamageSource#getSource(): Entity                           (method_5526)
 *   ItemStack#damage(int, LivingEntity, Consumer<LivingEntity>) (method_7956)
 *   ItemStack#getMaxDamage(): int                              (method_7936)
 *   ItemStack#isEmpty(): boolean                                (method_7960)
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
 * DamageSource#getSource() (оба наследуют AbstractFireballEntity, но сами
 * классы разные и не наследуют друг друга).
 *
 * Наша версия: если блок щитом сработал (blockedByShield == true) и
 * источник — фаербол ифрита или гаста, ПОВЕРХ ванильных ~6 прочности за
 * блок (эту часть не трогаем, она отрабатывает своим путём где-то ещё в
 * damage()) вешаем счётчик кастомного DoT на прочность щита: 1% от
 * getMaxDamage() щита в секунду, 3 сек для ифрита / 10 сек для гаста.
 *
 * ItemStack#damage(int, ...) принимает int, поэтому 1% от maxDamage
 * округляется (Math.round), с гарантией минимум 1 прочности за тик, чтобы
 * эффект не обнулился на предметах с маленьким maxDamage.
 *
 * Держим прямую ссылку на объект ItemStack щита (не спрашиваем
 * getActiveItem() заново каждый тик) — если игрок отпустит ПКМ раньше, чем
 * закончится наш DoT, добиваем именно тот стак, которым блокировали, а не
 * то, что у него в руке прямо сейчас.
 */
@Mixin(LivingEntity.class)
public abstract class ShieldFireMixin {

    @Unique
    private int noabuse$burnSecondsRemaining = 0;

    @Unique
    private int noabuse$burnTickCounter = 0;

    @Unique
    private int noabuse$burnDamagePerSecond = 0;

    @Unique
    private ItemStack noabuse$burnShieldStack = null;

    @Unique
    private Hand noabuse$burnHand = null;

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

        ItemStack activeStack = self.getActiveItem();
        if (activeStack == null || activeStack.isEmpty()) {
            return; // подстраховка, в теории сюда не попадём раз блок сработал
        }

        noabuse$burnSecondsRemaining = seconds;
        noabuse$burnTickCounter = 0;
        noabuse$burnShieldStack = activeStack;
        noabuse$burnHand = self.getActiveHand();
        noabuse$burnDamagePerSecond = Math.max(1, Math.round(activeStack.getMaxDamage() * 0.01f));
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void noabuse$onTickMovement(CallbackInfo ci) {
        if (noabuse$burnSecondsRemaining <= 0) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;

        // считаем урон прочности только на сервере — на клиенте это привело
        // бы к рассинхрону визуальной прочности с реальной
        if (self.getWorld().isClient) {
            return;
        }

        noabuse$burnTickCounter++;
        if (noabuse$burnTickCounter < 20) {
            return; // ждём полную секунду (20 тиков)
        }

        noabuse$burnTickCounter = 0;
        noabuse$burnSecondsRemaining--;

        if (noabuse$burnShieldStack != null && !noabuse$burnShieldStack.isEmpty()) {
            Hand hand = noabuse$burnHand;
            noabuse$burnShieldStack.damage(noabuse$burnDamagePerSecond, self, e -> e.sendToolBreakStatus(hand));
        }

        if (noabuse$burnSecondsRemaining <= 0) {
            noabuse$burnShieldStack = null;
            noabuse$burnHand = null;
        }
    }
}
