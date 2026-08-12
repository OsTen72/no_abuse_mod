package com.mamaika.noabuse.mixin;

import net.minecraft.entity.EyeOfEnderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Ваниль (подтверждено официальным маппингом Yarn, метод method_7478 =
 * initTargetPos(BlockPos), и историческим исходником старой EntityEnderSignal,
 * где та же логика называлась "a(double, int, double)"):
 *
 *   this.survives = this.random.nextInt(5) > 0;
 *
 * То есть шанс разбиться — 1 из 5 (20%), задаётся ОДИН РАЗ в момент броска
 * на поиск крепости, внутри initTargetPos(BlockPos) — а не в tick(), как я
 * ошибочно предполагал в первой версии (отсюда и краш).
 *
 * Наша версия: подменяем константу 5 на 1. nextInt(1) всегда возвращает 0,
 * а "0 > 0" всегда false — то есть "survives" всегда false, и око бьётся
 * гарантированно. На портал это не влияет — там другой метод/код.
 */
@Mixin(EyeOfEnderEntity.class)
public class EyeOfEnderMixin {

    @ModifyConstant(method = "initTargetPos", constant = @Constant(intValue = 5))
    private int noabuse$alwaysShatter(int original) {
        return 1;
    }
}
