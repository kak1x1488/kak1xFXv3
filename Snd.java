package dev.kak1x.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Звуки киллэффектов. Один и тот же сценарий для мира и для меню. */
final class Snd {
    private static final int[] ANGEL_NOTES = {0, 4, 7, 12, 16, 19};

    private Snd() {}

    static float st(int semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    static void play(SoundEvent s, float vol, float pitch, double x, double y, double z, boolean menu) {
        Config c = Config.I;
        if (!c.sound || c.volume <= 0.001f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float v = vol * c.volume;
        if (menu) {
            mc.getSoundManager().play(PositionedSoundInstance.master(s, pitch, v));
        } else {
            ClientWorld w = mc.world;
            if (w == null) return;
            mc.getSoundManager().play(new PositionedSoundInstance(
                    s, SoundCategory.PLAYERS, v, pitch, w.getRandom(), x, y, z));
        }
    }

    /** Вызывается на каждом тике возраста эффекта (0,1,2...). */
    static void schedule(Effect e, int t, double x, double y, double z, boolean m) {
        switch (e) {
            case SOUL_REAPER -> {
                if (t == 0) {
                    play(SoundEvents.ENTITY_VEX_DEATH, 2f, 0.6f, x, y, z, m);
                    play(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f, x, y, z, m);
                }
                if (t == 31) play(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 2f, 0.6f, x, y, z, m);
            }
            case ANGEL -> {
                // восходящее арпеджио хрусталя = «ангельский» перезвон
                if (t % 2 == 0 && t <= 10) {
                    play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.8f, st(ANGEL_NOTES[t / 2]), x, y, z, m);
                }
                if (t == 0) play(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9f, 1.9f, x, y, z, m);
                if (t == 12) play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 1.6f, x, y, z, m);
                if (t == 14) play(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f, x, y, z, m);
            }
            case THUNDER -> {
                if (t == 6) {
                    play(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 1.0f, x, y, z, m);
                    play(SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 3f, 1.1f, x, y, z, m);
                }
            }
            case HELLFIRE -> {
                if (t == 0) {
                    play(SoundEvents.ENTITY_BLAZE_SHOOT, 2f, 0.7f, x, y, z, m);
                    play(SoundEvents.ITEM_FIRECHARGE_USE, 2f, 0.8f, x, y, z, m);
                }
                if (t == 8) play(SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 2f, 1.2f, x, y, z, m);
            }
            case GLACIAL -> {
                if (t == 0) play(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 2f, 0.8f, x, y, z, m);
                if (t == 14) {
                    play(SoundEvents.BLOCK_GLASS_BREAK, 3f, 0.7f, x, y, z, m);
                    play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 1.5f, x, y, z, m);
                }
            }
            case BLOOD -> {
                if (t == 0) play(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2f, 0.6f, x, y, z, m);
            }
            case NOVA -> {
                if (t == 0) {
                    play(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 1.2f, x, y, z, m);
                    play(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.6f, x, y, z, m);
                }
                if (t == 10) play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.8f, x, y, z, m);
            }
            case GHOST -> {
                if (t == 0) {
                    play(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.6f, x, y, z, m);
                    play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.8f, x, y, z, m);
                }
                if (t == 8) play(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 1.5f, 1.4f, x, y, z, m);
            }
            case BLACKHOLE -> {
                if (t == 0) play(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.8f, x, y, z, m);
                if (t == 20) play(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 1.0f, x, y, z, m);
            }
            case SHATTER -> {
                if (t == 0) {
                    play(SoundEvents.BLOCK_GLASS_BREAK, 3f, 1.4f, x, y, z, m);
                    play(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 2f, 1.2f, x, y, z, m);
                }
            }
        }
    }
}
