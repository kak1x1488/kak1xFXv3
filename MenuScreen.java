package dev.kak1x.fx;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class MenuScreen extends Screen {
    private static final int W = 460, H = 292;

    private final MenuAnim anim = new MenuAnim();
    private final Effect[] list = Effect.values();
    private final ButtonWidget[] effBtns = new ButtonWidget[list.length];
    private ButtonWidget enabledBtn, soundBtn, qualityBtn, distBtn;
    private long last = System.nanoTime();
    private int left, top, sx0, sy0, sx1, sy1;

    public MenuScreen() {
        super(Text.literal("kak1xFX"));
    }

    private static Text volText(double v) {
        return Text.literal("Громкость: " + Math.round(v * 100) + "%");
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        sx0 = left + 126;
        sy0 = top + 28;
        sx1 = left + 316;
        sy1 = top + 216;

        for (int i = 0; i < list.length; i++) {
            final Effect e = list[i];
            effBtns[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> select(e))
                    .dimensions(left + 8, top + 28 + i * 19, 112, 17).build());
        }

        int rx = left + 324, rw = 128, y = top + 28;

        enabledBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            Config.I.enabled = !Config.I.enabled;
            refresh();
        }).dimensions(rx, y, rw, 20).build());
        y += 24;

        soundBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            Config.I.sound = !Config.I.sound;
            refresh();
        }).dimensions(rx, y, rw, 20).build());
        y += 24;

        addDrawableChild(new SliderWidget(rx, y, rw, 20, volText(Config.I.volume), Config.I.volume) {
            @Override
            protected void updateMessage() {
                setMessage(volText(this.value));
            }

            @Override
            protected void applyValue() {
                Config.I.volume = (float) this.value;
            }
        });
        y += 24;

        qualityBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            Config.I.quality = (Config.I.quality + 1) % 3;
            refresh();
        }).dimensions(rx, y, rw, 20).build());
        y += 24;

        distBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            int idx = 0;
            for (int i = 0; i < Config.DIST.length; i++) if (Config.DIST[i] == Config.I.distance) idx = i;
            Config.I.distance = Config.DIST[(idx + 1) % Config.DIST.length];
            refresh();
        }).dimensions(rx, y, rw, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Повторить анимацию"), b -> restartAnim())
                .dimensions(rx, y, rw, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Показать в мире"), b -> {
            close();
            Kak1xFX.preview();
        }).dimensions(rx, y, rw, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
                .dimensions(rx, y, rw, 20).build());

        refresh();
        restartAnim();
    }

    private void select(Effect e) {
        Config.I.effect = e.name();
        refresh();
        restartAnim();
    }

    private void refresh() {
        Effect cur = Config.I.effect();
        for (int i = 0; i < list.length; i++) {
            effBtns[i].setMessage(list[i] == cur
                    ? Text.literal("» " + list[i].title).formatted(Formatting.GOLD)
                    : Text.literal(list[i].title));
        }
        enabledBtn.setMessage(Text.literal("Эффекты: " + (Config.I.enabled ? "ВКЛ" : "ВЫКЛ")));
        soundBtn.setMessage(Text.literal("Звук: " + (Config.I.sound ? "ВКЛ" : "ВЫКЛ")));
        qualityBtn.setMessage(Text.literal("Качество: " + Config.QUALITY_NAMES[Math.max(0, Math.min(2, Config.I.quality))]));
        distBtn.setMessage(Text.literal("Дальность: " + Config.I.distance + " м"));
    }

    private boolean hasPlayer() {
        return client != null && client.player != null;
    }

    private Identifier skin() {
        return client.player.getSkin().body().texturePath();
    }

    private boolean slim() {
        return String.valueOf(client.player.getSkin().model()).toLowerCase().contains("slim");
    }

    private void restartAnim() {
        anim.reset(Config.I.effect(), sx0, sy0, sx1, sy1, 3, hasPlayer() && slim());
        last = System.nanoTime();
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // без размытия и затемнения: мир остаётся видимым
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        anim.advance(dt);
        if (anim.done()) restartAnim();

        Effect e = Config.I.effect();
        int accent = 0xFF000000 | e.color;

        // окно
        c.fillGradient(left, top, left + W, top + H, 0xF00E0E16, 0xF0181826);
        c.fill(left, top, left + W, top + 2, accent);
        c.drawStrokedRectangle(left, top, W, H, 0xFF2E2E44);
        c.fill(left + 6, top + 26, left + 122, top + 218, 0x90000000);
        c.fill(left + 320, top + 26, left + W - 6, top + 218, 0x60000000);

        // сцена
        c.fillGradient(sx0, sy0, sx1, sy1, 0xFF10101A, 0xFF26263C);
        c.fill(sx0, sy1 - 14, sx1, sy1, 0xFF1A1A28);
        c.fill(sx0, sy1 - 14, sx1, sy1 - 13, 0x55FFFFFF);
        c.drawStrokedRectangle(sx0, sy0, sx1 - sx0, sy1 - sy0, 0xFF2E2E44);

        if (hasPlayer()) {
            if (!anim.killed()) {
                int cx = Math.round(anim.modelCenterX()), cy = Math.round(anim.modelCenterY());
                int size = Math.round(anim.figureHeight() / 1.8f);
                InventoryScreen.drawEntity(c, cx - 80, cy - 80, cx + 80, cy + 80, size, 0.0625f, mouseX, mouseY, client.player);
            } else {
                anim.render(c, skin());
            }
        }

        super.render(c, mouseX, mouseY, delta);

        // заголовок
        c.drawTextWithShadow(textRenderer, Text.literal("kak1xFX"), left + 10, top + 9, accent);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("Килл-эффекты"), left + W / 2, top + 9, 0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer, Text.literal("ESC - закрыть"), left + W - 82, top + 9, 0xFF808090);

        // подсказка над сценой
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(e.title), (sx0 + sx1) / 2, sy0 + 5, accent);

        // инфо-панель
        c.fill(left + 6, top + 222, left + W - 6, top + H - 6, 0x70000000);
        int ix = left + 12, iy = top + 227;
        c.drawTextWithShadow(textRenderer, Text.literal("Звук: " + (Config.I.sound ? e.soundDesc : "выключен")), ix, iy, 0xFFFFD27F);
        c.drawTextWithShadow(textRenderer, Text.literal("Эффект: " + e.fxDesc), ix, iy + 12, 0xFF9FE6FF);
        c.drawTextWithShadow(textRenderer, Text.literal("Где: " + e.whereDesc), ix, iy + 24, 0xFFB7FFB0);
        c.drawTextWithShadow(textRenderer,
                Text.literal("Команды: /kak1xFX menu   /kak1xFX test"), ix, iy + 38, 0xFF808090);
    }
}
