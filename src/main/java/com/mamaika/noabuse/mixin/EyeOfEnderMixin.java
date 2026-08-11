package com.mamaika.noabuse.mixin;

import net.minecraft.entity.EyeOfEnderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Ваниль: брошенное на поиск крепости око эндера имеет 20% шанс разбиться
 * (константа 0.2F в tick()), 80% — падает и его можно подобрать. Это НЕ
 * относится к оку, которое ставится в портал — там другой код (использование
 * предмета по блоку), этот миксин его не трогает.
 *
 * Наша версия: шанс разбиться делаем гарантированным (100%), так что каждый
 * бросок "на поиск" тратит око безвозвратно, а не по рандому.
 *
 * РИСК: если Yarn 1.20.1 назвал метод/константу иначе, чем tick()/0.2F,
 * Mixin выдаст явную ошибку при старте игры ("Constant not found") —
 * пришли мне текст, поправлю.
 */
@Mixin(EyeOfEnderEntity.class)
public class EyeOfEnderMixin {

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.2F))
    private float noabuse$alwaysShatter(float original) {
        return 1.0F;
    }
}
