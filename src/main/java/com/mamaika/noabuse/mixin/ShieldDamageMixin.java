package com.mamaika.noabuse.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Сверено по официальным Yarn-маппингам на билде проекта
 * (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит 9672e1f):
 *
 *   LivingEntity#damageShield(float amount): void   (method_6056)
 *
 * Это ровно тот метод, который ваниль вызывает для расчёта потери
 * прочности щита при УСПЕШНОМ блоке — неважно, чем били: мобом, игроком,
 * стрелой, файерболом и т.д. Один параметр "amount" — сколько прочности
 * снять. Утраиваем его на входе через @ModifyVariable (это штатный
 * способ Mixin поменять значение параметра метода, в отличие от @Inject,
 * где CallbackInfo не даёт менять входные аргументы).
 *
 * Взамен снятой прошлой версии (ShieldFireMixin с DoT только на
 * ифрита/гаста) — тут всё сразу и для всех, без разбора по мобам/классам
 * снарядов, поэтому и меньше точек, где могло что-то пойти не так.
 */
@Mixin(net.minecraft.entity.LivingEntity.class)
public abstract class ShieldDamageMixin {

    @ModifyVariable(method = "damageShield", at = @At("HEAD"), argsOnly = true)
    private float noabuse$tripleShieldDamage(float amount) {
        return amount * 3.0f;
    }
}
