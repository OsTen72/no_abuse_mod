package com.mamaika.noabuse.mixin;

import com.mamaika.noabuse.NoAbuseMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Все методы ниже сверены с официальными Yarn-маппингами на билде проекта
 * (yarn_mappings=1.20.1+build.10, ветка FabricMC/yarn, коммит 9672e1f):
 *
 *   Item#use(World, PlayerEntity, Hand): TypedActionResult<ItemStack>
 *                                                          (method_7836)
 *   TypedActionResult#getValue(): Object                  (method_5466)
 *   ItemStack#isOf(Item): boolean                         (method_31574)
 *   PlayerEntity#getItemCooldownManager(): ItemCooldownManager
 *                                                          (method_7357)
 *   ItemCooldownManager#set(Item, int duration): void     (method_7906)
 *
 * ВАЖНАЯ ОГОВОРКА (в отличие от всех прошлых файлов в этой сессии):
 * метод "use" НЕ числится напрямую в BucketItem.mapping (и в BowItem.mapping
 * тоже, хотя лук точно переопределяет use() под натяжение тетивы) — то
 * есть мой обычный способ проверки "есть метод в файле класса — точно
 * переопределён" тут не сработал как индикатор. Похоже, Yarn не всегда
 * дублирует запись METHOD в файле подкласса просто для чистого
 * переопределения без новых имён параметров — то есть отсутствие записи
 * НЕ доказывает отсутствие переопределения (в отличие от вывода, который
 * я делал раньше для tickMovement/getDimensions, там есть больше
 * оснований доверять, но тоже не 100% железно).
 *
 * Поэтому здесь полагаюсь на общеизвестный факт устройства Minecraft/
 * Fabric-моддинга (не на маппинги): BucketItem переопределяет
 * Item#use(), это стандартная, годами задокументированная механика
 * (вычерпывание/выливание жидкости идёт именно через неё). Но я не могу
 * прогнать игру сам, чтобы подтвердить на 100% — если при старте будет
 * краш "Unable to locate target method" на этом файле, значит здесь я
 * ошибся, и метод называется/устроен иначе в 1.20.1.
 *
 * Логика: after RETURN исходного use(), если полученный ItemStack —
 * пустое ведро (значит вода/лава только что были вылиты), вешаем игроку
 * кулдаун на предмет "пустое ведро" — не даёт тут же зачерпнуть жидкость
 * обратно, разлитая жидкость успевает натворить дел (смыть редстоун,
 * факелы и т.п.) до того как её можно будет убрать.
 *
 * cooldown = 30 тиков = 1.5 сек (запрошенный диапазон был 1-2 сек).
 */
@Mixin(BucketItem.class)
public abstract class BucketCooldownMixin {

    @Inject(method = "use", at = @At("RETURN"))
    private void noabuse$cooldownAfterEmpty(World world, PlayerEntity player, Hand hand,
                                             CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (world.isClient) {
            return;
        }

        ItemStack resultStack = cir.getReturnValue().getValue();

        // ВРЕМЕННЫЙ ДИАГНОСТИЧЕСКИЙ ЛОГ — сработал ли инжект вообще, и
        // что реально лежит в resultStack. После разбора — уберём.
        NoAbuseMod.LOGGER.info(
                "[NoAbuse][DEBUG] bucket use() fired, result={}, actionResult={}",
                resultStack.getItem(),
                cir.getReturnValue().getResult()
        );

        if (resultStack.isOf(Items.BUCKET)) {
            player.getItemCooldownManager().set(Items.BUCKET, 30);
            NoAbuseMod.LOGGER.info("[NoAbuse][DEBUG] bucket cooldown SET");
        }
    }
}
