package com.mamaika.noabuse.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Все методы ниже сверены с официальными Yarn-маппингами на билде проекта
 * (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит 9672e1f):
 *
 *   EndermanEntity#getTarget(): LivingEntity      (унаследовано от
 *                                интерфейса Targeter, method_5968)
 *   LivingEntity#tryAttack(Entity): boolean        (method_6121)
 *   LivingEntity#canSee(Entity): boolean           (method_6057)
 *   Entity#squaredDistanceTo(Entity): double       (method_5858)
 *   LivingEntity#tickMovement(): void              (method_6007)
 *
 * ВАЖНО: EndermanEntity НЕ переопределяет tickMovement()/tick() сам —
 * этого метода нет в его собственном классе по маппингам (только в
 * LivingEntity/Entity выше по цепочке наследования). Поэтому, как и с
 * ShieldFireMixin, вешаемся на @Mixin(LivingEntity.class), а не
 * @Mixin(EndermanEntity.class) — иначе на старте был бы краш
 * "Unable to locate target method".
 *
 * Что чиним: и "лодка на голове/под ифритом", и "присед в проёме 1x1" —
 * это, по сути, один и тот же класс багов: цель формально рядом и видна
 * (canSee/squaredDistanceTo это подтверждают), но встроенная ванильная
 * Goal-логика атаки почему-то не долетает до реального удара — либо из-за
 * смещения хитбокса при посадке в лодку, либо из-за того, что ифрит
 * физически не пролезает габаритами в 1-блочный проём, чтобы штатный
 * MeleeAttackGoal "закрыл дистанцию".
 *
 * Копать точную первопричину внутри Goal-классов без декомпиленного кода —
 * долго и ненадёжно гадать. Вместо этого просто обходим её: раз в секунду
 * (20 тиков), если у ифрита есть цель, она жива, в пределах ~3 блоков и
 * видна — бьём tryAttack() напрямую, не дожидаясь, пока родная AI-логика
 * сама решит, что дотянулась. Не важно, что именно мешало ванильной атаке
 * (лодка, проём, что-то ещё подобное) — если по факту рядом и видно,
 * получит урон.
 *
 * Побочный эффект: это работает не только против абузов, но и в обычном
 * честном бою — ифрит будет добавлять свою секундную "проверку на удар"
 * поверх штатной атаки. Ритм примерно совпадает с обычной скоростью атаки
 * мобов, так что в честном бою не должно ощущаться как значимый бафф —
 * просто страховка на случай, если штатная атака промахнулась мимо
 * реальности по вышеописанным причинам.
 */
@Mixin(LivingEntity.class)
public abstract class EndermanAttackMixin {

    @Unique
    private int noabuse$endermanAttackCooldown = 0;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void noabuse$forceEndermanAttack(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // считаем и бьём только на сервере — на клиенте это привело бы
        // к рассинхрону урона/анимаций с реальным состоянием
        if (self.getWorld().isClient) {
            return;
        }

        if (!(self instanceof EndermanEntity enderman)) {
            return;
        }

        if (noabuse$endermanAttackCooldown > 0) {
            noabuse$endermanAttackCooldown--;
            return;
        }

        LivingEntity target = enderman.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        if (enderman.squaredDistanceTo(target) > 9.0) {
            return; // дальше ~3 блоков — пусть просто идёт/телепортируется как обычно
        }

        if (!enderman.canSee(target)) {
            return;
        }

        enderman.tryAttack(target);
        noabuse$endermanAttackCooldown = 20; // раз в секунду, как обычный ритм атаки
    }
}
