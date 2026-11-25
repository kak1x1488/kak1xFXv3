package dev.kak1x.fx;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class Kak1xFX implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kak1xfx");

    private static final class Tracked {
        final LivingEntity e;
        int age;
        Tracked(LivingEntity e) { this.e = e; }
    }

    private static final ArrayList<Tracked> TRACKED = new ArrayList<>(8);

    @Override
    public void onInitializeClient() {
        Config.load();

        // запоминаем тех, кого ты ударил
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()
                    && entity instanceof LivingEntity le
                    && entity != MinecraftClient.getInstance().player) {
                for (int i = 0; i < TRACKED.size(); i++) {
                    if (TRACKED.get(i).e == le) {
                        TRACKED.get(i).age = 0;
                        return ActionResult.PASS;
                    }
                }
                if (TRACKED.size() < 32) TRACKED.add(new Tracked(le));
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(Kak1xFX::tick);

        // имя команды чувствительно к регистру, поэтому регистрируем оба варианта
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            register(dispatcher, "kak1xFX");
            register(dispatcher, "kak1xfx");
        });
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> d, String name) {
        d.register(ClientCommandManager.literal(name)
                .executes(ctx -> { openMenu(); return 1; })
                .then(ClientCommandManager.literal("menu").executes(ctx -> { openMenu(); return 1; }))
                .then(ClientCommandManager.literal("test").executes(ctx -> { preview(); return 1; })));
    }

    public static void openMenu() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // через send(), иначе чат закроет меню сразу после команды
        mc.send(() -> mc.setScreen(new MenuScreen()));
    }

    /** Показать выбранный эффект перед игроком. */
    public static void preview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        double yaw = Math.toRadians(mc.player.getYaw());
        double x = mc.player.getX() - Math.sin(yaw) * 3.5;
        double z = mc.player.getZ() + Math.cos(yaw) * 3.5;
        WorldFx.start(Config.I.effect(), mc.world, x, mc.player.getY(), z, 1.8);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null) {
            TRACKED.clear();
            WorldFx.clear();
            return;
        }

        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            Tracked t = TRACKED.get(i);
            LivingEntity e = t.e;
            t.age++;
            boolean dead = e.isDead() || e.getHealth() <= 0.0f;
            if (dead || e.isRemoved() || t.age > 80) {
                int last = TRACKED.size() - 1;
                TRACKED.set(i, TRACKED.get(last));
                TRACKED.remove(last);
                if (dead && Config.I.enabled) {
                    WorldFx.start(Config.I.effect(), mc.world, e.getX(), e.getY(), e.getZ(), e.getHeight());
                }
            }
        }

        if (!WorldFx.idle()) WorldFx.tick(mc.world);
    }
}
