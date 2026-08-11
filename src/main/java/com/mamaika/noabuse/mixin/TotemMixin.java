package com.mamaika.noabuse.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ваниль: тотем спасает от смерти И даёт Regeneration II + Absorption II + Fire Resistance
 * на несколько секунд, что превращает "шанс на жизнь" в "продолжай фейстанк".
 *
 * Наша версия: тотем всё ещё спасает (ставит 1 HP, как в ваниле — эту часть
 * трогать не нужно, она отрабатывает до RETURN), но вместо баффов даём голод
 * и тошноту, а также сбрасываем сытость — чтобы после спасения пришлось
 * реально отступать и лечиться, а не продолжать бой.
 */
@Mixin(LivingEntity.class)
public abstract class TotemMixin {

    @Inject(method = "tryUseTotem", at = @At("RETURN"))
    private void noabuse$onTotemUsed(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return; // тотема не было — ничего не делаем
        }

        LivingEntity self = (LivingEntity) (Object) this;

        // убираем ванильные "спасательные" баффы
        self.removeStatusEffect(StatusEffects.REGENERATION);
        self.removeStatusEffect(StatusEffects.ABSORPTION);
        self.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);

        // и вешаем цену за выживание
        self.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 30 * 20, 1));
        self.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 15 * 20, 0));

        if (self instanceof ServerPlayerEntity player) {
            player.getHungerManager().setFoodLevel(Math.min(player.getHungerManager().getFoodLevel(), 6));
            player.getHungerManager().setSaturationLevel(0f);
        }
    }
}
