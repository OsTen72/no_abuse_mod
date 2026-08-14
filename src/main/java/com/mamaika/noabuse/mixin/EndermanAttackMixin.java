package com.mamaika.noabuse.mixin;

import com.mamaika.noabuse.NoAbuseMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Все методы ниже сверены с официальными Yarn-маппингами на билде проекта
 * (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит 9672e1f):
 *
 *   EndermanEntity#isAngry(): boolean                       (method_7028)
 *   LivingEntity#tryAttack(Entity): boolean                 (method_6121)
 *   LivingEntity#canSee(Entity): boolean                    (method_6057)
 *   LivingEntity#tickMovement(): void                       (method_6007)
 *   World#getClosestPlayer(Entity, double): PlayerEntity     (унаследовано
 *                            от интерфейса EntityView, method_18460)
 *
 * ВАЖНО: EndermanEntity НЕ переопределяет tickMovement()/tick() сам —
 * этого метода нет в его собственном классе по маппингам (только в
 * LivingEntity/Entity выше по цепочке наследования). Поэтому вешаемся на
 * @Mixin(LivingEntity.class), а не @Mixin(EndermanEntity.class).
 *
 * ПОЧЕМУ ПЕРВАЯ ВЕРСИЯ НЕ РАБОТАЛА (эндермен бил только с 1 клетки, как
 * и раньше): предыдущая версия проверяла EndermanEntity#getTarget().
 * По всей видимости, это поле у эндермена заполняется именно его штатной
 * Goal-логикой БЛИЖНЕЙ атаки, а не в момент, когда он просто "разозлился"
 * и телепортируется к игроку, на которого тот таращится (это отдельная
 * внутренняя логика в TeleportTowardsPlayerGoal, до общего getTarget() не
 * долетающая). То есть пока эндермен идёт/телепортируется к игроку,
 * getTarget() у него, скорее всего, ещё null — и становится не-null уже
 * тогда, когда он и без нас готов ударить в упор. Отсюда и ощущение "как
 * било с одной клетки, так и бьёт".
 *
 * Теперь вместо getTarget() берём isAngry() — это флаг "разозлён"
 * (синхронизируется на клиент для красных глаз), выставляется сразу при
 * провокации взглядом, задолго до физического сближения. И вместо
 * "спрошенной у эндермена цели" явно ищем ближайшего игрока в радиусе
 * дистанции атаки через сам мир (getClosestPlayer), не полагаясь на
 * внутренний таргетинг ИИ вообще.
 *
 * Оговорка: если на сервере несколько игроков и эндермен разозлён на
 * ОДНОГО, а физически ближе другой — ударит ближайшего, не обязательно
 * того самого "виновника". Для одиночной игры/малого сервера это не
 * играет роли.
 *
 * Дистанция: PLAYER_REACH_BLOCKS = 3.0 — стандартная ванильная дистанция
 * атаки игрока по сущностям в выживании (устоявшееся значение много
 * версий подряд, на нём основаны reach-детекторы в античитах). Задача —
 * чтобы эндермен мог бить с той же дистанции, с которой бьёшь его ты.
 */
@Mixin(LivingEntity.class)
public abstract class EndermanAttackMixin {

    @Unique
    private static final double PLAYER_REACH_BLOCKS = 3.0;

    @Unique
    private int noabuse$endermanAttackCooldown = 0;

    @Unique
    private int noabuse$debugLogCooldown = 0;

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

        boolean angry = enderman.isAngry();
        PlayerEntity nearestPlayer = self.getWorld().getClosestPlayer(enderman, PLAYER_REACH_BLOCKS);
        boolean sees = nearestPlayer != null && enderman.canSee(nearestPlayer);

        // ВРЕМЕННЫЙ ДИАГНОСТИЧЕСКИЙ ЛОГ — раз в секунду, только пока
        // энтити хоть немного "в игре" (не спамит на всех эндерменов
        // карты, только на тех, у кого angry=true хотя бы раз было).
        // После того как разберёмся — уберём.
        if (noabuse$debugLogCooldown > 0) {
            noabuse$debugLogCooldown--;
        } else if (angry) {
            noabuse$debugLogCooldown = 20;
            double dist = nearestPlayer != null ? Math.sqrt(enderman.squaredDistanceTo(nearestPlayer)) : -1;
            NoAbuseMod.LOGGER.info(
                    "[NoAbuse][DEBUG] enderman angry={} nearestPlayer={} dist={} sees={} cooldown={}",
                    angry,
                    nearestPlayer != null ? nearestPlayer.getName().getString() : "null",
                    dist,
                    sees,
                    noabuse$endermanAttackCooldown
            );
        }

        if (noabuse$endermanAttackCooldown > 0) {
            noabuse$endermanAttackCooldown--;
            return;
        }

        if (!angry || nearestPlayer == null || !nearestPlayer.isAlive() || !sees) {
            return;
        }

        enderman.tryAttack(nearestPlayer);
        noabuse$endermanAttackCooldown = 20; // раз в секунду, как обычный ритм атаки
    }
}
