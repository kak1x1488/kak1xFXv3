package dev.kak1x.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kak1xfx.json");

    public static final float[] QUALITY = {0.35f, 0.65f, 1.0f};
    public static final String[] QUALITY_NAMES = {"Низкое", "Среднее", "Высокое"};
    public static final int[] DIST = {16, 32, 48, 64};

    public static Config I = new Config();

    public boolean enabled = true;
    public boolean sound = true;
    public float volume = 1.0f;
    public String effect = "ANGEL";
    public int quality = 2;   // индекс в QUALITY
    public int distance = 48; // дальше этого эффект не рисуется
    public int maxActive = 6; // максимум одновременных эффектов

    public Effect effect() {
        try {
            return Effect.valueOf(effect);
        } catch (Exception e) {
            return Effect.ANGEL;
        }
    }

    public float qmul() {
        int q = Math.max(0, Math.min(2, quality));
        return QUALITY[q];
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            Kak1xFX.LOGGER.warn("Не удалось загрузить конфиг", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(I));
        } catch (Exception e) {
            Kak1xFX.LOGGER.warn("Не удалось сохранить конфиг", e);
        }
    }
}
