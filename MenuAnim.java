package dev.kak1x.fx;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * 2D-анимация килл-эффекта прямо на твоём скине в меню.
 * Скин режется на ячейки 2x2 пикселя текстуры; они летят/окрашиваются/тают по сценарию эффекта.
 * Оптимизация: пул частиц на примитивных массивах (0 аллокаций в кадре), симуляция 60 Гц
 * независимо от FPS, отрисовка только внутри сцены, лимит частиц.
 */
final class MenuAnim {
    static final int MAXP = 640, MAXC = 100, ALIVE = 54, LOOP = 330;

    // частицы
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP],
            pg = new float[MAXP], pl = new float[MAXP], pm = new float[MAXP], ps = new float[MAXP];
    private final int[] pc = new int[MAXP];
    private int pn;

    // ячейки скина
    private final float[] cx = new float[MAXC], cy = new float[MAXC], cvx = new float[MAXC], cvy = new float[MAXC];
    private final int[] cu = new int[MAXC], cv = new int[MAXC], cw = new int[MAXC], ch = new int[MAXC];
    private int cn;

    private Effect fx = Effect.ANGEL;
    private int S = 3, steps, ks, stage;
    private boolean killed, sil;
    private float acc, T, cg, cd = 1f, fadeS = 60, fadeE = 120, flash;
    private int tint = 0xFFFFFF, silCol = 0xFF7AD9;
    private float stCx, stTop, stBottom, stL, stR, ground, fig0x, fig0y;
    private final float[] bx = new float[16], by = new float[16];
    private int bn;

    boolean killed() { return killed; }
    boolean done() { return steps >= LOOP; }
    float modelCenterX() { return stCx; }
    float modelCenterY() { return fig0y + 16 * S; }
    int figureHeight() { return 32 * S; }

    // ---------------------------------------------------------------- setup
    void reset(Effect e, float l, float t, float r, float b, int scale, boolean slim) {
        fx = e;
        S = scale;
        stL = l; stTop = t; stR = r; stBottom = b;
        stCx = (l + r) / 2f;
        ground = b - 14;
        fig0x = stCx - 8 * S;
        fig0y = ground - 32 * S;
        steps = 0; ks = 0; T = 0; stage = 0;
        killed = false; sil = false;
        pn = 0; cn = 0; acc = 0; flash = 0; bn = 0;
        tint = 0xFFFFFF; cg = 0; cd = 1f; fadeS = 60; fadeE = 120;
        int aw = slim ? 3 : 4;
        part(4, 0, 8, 8, 8, 8);                 // голова
        part(4, 8, 8, 12, 20, 20);              // тело
        part(slim ? 1 : 0, 8, aw, 12, 44, 20);  // правая рука
        part(12, 8, aw, 12, 36, 52);            // левая рука
        part(4, 20, 4, 12, 4, 20);              // правая нога
        part(8, 20, 4, 12, 20, 52);             // левая нога
    }

    private void part(int fx0, int fy0, int w, int h, int u, int v) {
        for (int yy = 0; yy < h; yy += 2) {
            for (int xx = 0; xx < w; xx += 2) {
                if (cn >= MAXC) return;
                int i = cn++;
                cw[i] = Math.min(2, w - xx);
                ch[i] = Math.min(2, h - yy);
                cx[i] = fig0x + (fx0 + xx) * S;
                cy[i] = fig0y + (fy0 + yy) * S;
                cu[i] = u + xx;
                cv[i] = v + yy;
                cvx[i] = 0;
                cvy[i] = 0;
            }
        }
    }

    private float cellCx(int i) { return cx[i] + cw[i] * S / 2f; }
    private float cellCy(int i) { return cy[i] + ch[i] * S / 2f; }

    // ---------------------------------------------------------------- частицы
    private void sp(float x, float y, float vx, float vy, float g, int life, float size, int col) {
        if (pn >= MAXP) return;
        int i = pn++;
        px[i] = x; py[i] = y; pvx[i] = vx; pvy[i] = vy; pg[i] = g;
        pl[i] = life; pm[i] = life; ps[i] = size; pc[i] = col;
    }

    private void toward(float x, float y, float tx, float ty, int life, float size, int col) {
        sp(x, y, (tx - x) / life, (ty - y) / life, 0, life, size, col);
    }

    private int q(int n) {
        return Math.max(1, Math.round(n * Config.I.qmul()));
    }

    private void burst(int n, float x, float y, float speed, float up, int life, float size, int col, float g) {
        for (int i = 0, m = q(n); i < m; i++) {
            sp(x, y, Rng.r(speed), Rng.r(speed) - up, g, life + (int) Rng.range(0, 14), size, col);
        }
    }

    private void killParticle(int i) {
        int l = --pn;
        px[i] = px[l]; py[i] = py[l]; pvx[i] = pvx[l]; pvy[i] = pvy[l];
        pg[i] = pg[l]; pl[i] = pl[l]; pm[i] = pm[l]; ps[i] = ps[l]; pc[i] = pc[l];
    }

    // ---------------------------------------------------------------- симуляция
    void advance(float dtSec) {
        acc += dtSec * 60f;
        int guard = 0;
        while (acc >= 1f && guard++ < 6) {
            acc -= 1f;
            step();
        }
    }

    private void step() {
        steps++;
        if (steps >= ALIVE) {
            ks = steps - ALIVE;
            T = ks / 3f;
            if (!killed) {
                killed = true;
                onKill();
            }
            if (ks % 3 == 0) Snd.schedule(fx, ks / 3, 0, 0, 0, true);
            perEffect();
            for (int i = 0; i < cn; i++) {
                cx[i] += cvx[i];
                cy[i] += cvy[i];
                cvx[i] *= cd;
                cvy[i] += cg;
            }
        }
        for (int i = 0; i < pn; ) {
            pvy[i] += pg[i];
            px[i] += pvx[i];
            py[i] += pvy[i];
            if (--pl[i] <= 0) killParticle(i);
            else i++;
        }
        if (flash > 0) flash = Math.max(0, flash - 0.05f);
    }

    private void onKill() {
        for (int i = 0; i < cn; i++) { cvx[i] = 0; cvy[i] = 0; }
        float midY = fig0y + 16 * S;
        switch (fx) {
            case SOUL_REAPER -> {
                tint = 0xC084FC; cg = -0.0035f; cd = 0.997f; fadeS = 75; fadeE = 150;
                for (int i = 0; i < cn; i++) { cvy[i] = -Rng.range(.08f, .3f); cvx[i] = Rng.r(.12f); }
                burst(40, stCx, midY, 1.2f, 0.4f, 40, 2.5f, 0xA855F7, 0);
            }
            case ANGEL -> {
                tint = 0xFFF1B8; cg = -0.0012f; fadeS = 95; fadeE = 165;
                for (int i = 0; i < cn; i++) cvy[i] = -Rng.range(.1f, .2f);
                burst(30, stCx, midY, 1.0f, 0.5f, 40, 2.5f, 0xFFE08A, 0);
            }
            case THUNDER -> {
                fadeS = 100; fadeE = 150;
                makeBolt();
            }
            case HELLFIRE -> {
                tint = 0xFF8A3D; cg = -0.003f; fadeS = 70; fadeE = 130;
                for (int i = 0; i < cn; i++) { cvy[i] = -Rng.range(.15f, .45f); cvx[i] = Rng.r(.25f); }
            }
            case GLACIAL -> { tint = 0xAEE8FF; fadeS = 95; fadeE = 140; }
            case BLOOD -> {
                tint = 0xD01818; cg = 0.085f; cd = 0.985f; fadeS = 95; fadeE = 150;
                for (int i = 0; i < cn; i++) { cvx[i] = (cellCx(i) - stCx) * 0.03f + Rng.r(1.2f); cvy[i] = -Rng.range(.5f, 2.8f); }
                burst(90, stCx, midY, 2.4f, 1.0f, 55, 3f, 0xC81414, 0.1f);
            }
            case NOVA -> { tint = 0x7CFFEE; fadeS = 90; fadeE = 140; }
            case GHOST -> {
                sil = true; silCol = 0xFF7AD9; cg = -0.001f; fadeS = 70; fadeE = 130;
                for (int i = 0; i < cn; i++) cvy[i] = -Rng.range(.1f, .2f);
            }
            case BLACKHOLE -> { tint = 0xD8B4FE; fadeS = 80; fadeE = 100; }
            case SHATTER -> {
                cg = 0.09f; cd = 0.99f; fadeS = 85; fadeE = 140;
                for (int i = 0; i < cn; i++) { cvx[i] = (cellCx(i) - stCx) * 0.04f + Rng.r(1.4f); cvy[i] = -Rng.range(.4f, 3.2f); }
                burst(30, stCx, midY, 1.2f, 0.4f, 50, 4f, 0x9CA3AF, 0.02f);
            }
        }
    }

    private void makeBolt() {
        bn = 14;
        float x = stCx + Rng.r(6f);
        float y0 = stTop, y1 = fig0y + 16 * S;
        for (int i = 0; i < bn; i++) {
            float f = i / (float) (bn - 1);
            bx[i] = x + Rng.r(10f) * (i == bn - 1 ? 0 : 1);
            by[i] = y0 + (y1 - y0) * f;
        }
        bx[bn - 1] = stCx;
    }

    private void perEffect() {
        float headY = fig0y, midY = fig0y + 16 * S, mid = stCx;
        switch (fx) {
            case SOUL_REAPER -> {
                if (T <= 30 && ks % 2 == 0) {
                    float an = Rng.f() * 6.283f, r = 60 + Rng.f() * 20;
                    toward(mid + (float) Math.cos(an) * r, headY - 16 + (float) Math.sin(an) * r * 0.7f, mid, headY - 16, 28, 3, 0x6D28D9);
                }
                if (T >= 31 && stage == 0) {
                    stage = 1;
                    burst(70, mid, headY - 16, 2.2f, 0, 40, 3, 0xA855F7, 0);
                    flash = 0.5f;
                }
            }
            case ANGEL -> {
                if (T <= 34 && ks % 3 == 0) sp(mid + Rng.r(40f), headY - 20, Rng.r(.1f), .25f, 0, 70, 2, 0xFFFFFF);
                if (T <= 20 && ks % 2 == 0) sp(mid + Rng.r(8f), ground, Rng.r(.1f), -.7f, 0, 40, 2, 0xFFE08A);
            }
            case THUNDER -> {
                if (T <= 6 && ks % 2 == 0) sp(mid + Rng.r(35f), stTop + 14 + Rng.r(6f), Rng.r(.1f), 0, 0, 120, 14, 0x3C4048);
                if (T >= 6 && stage == 0) {
                    stage = 1;
                    flash = 0.9f;
                    tint = 0x555555; cg = 0.045f;
                    burst(60, mid, midY, 3f, 0.5f, 30, 2, 0xBFD4FF, 0);
                }
                if (T >= 6 && T <= 12 && ks % 3 == 0) sp(mid + Rng.r(20f), midY + Rng.r(30f), Rng.r(.6f), Rng.r(.6f), 0, 10, 2, 0xFFFFFF);
            }
            case HELLFIRE -> {
                if (T <= 30) {
                    for (int i = 0, m = q(3); i < m; i++)
                        sp(mid + Rng.r(26f), ground - Rng.f() * 30, Rng.r(.2f), -Rng.range(.5f, 1.2f), 0, 28, 3, Rng.pick(0xFF4500, 0xFF8C00, 0xFFD166));
                }
            }
            case GLACIAL -> {
                if (T >= 24 && stage == 0) {
                    stage = 1;
                    cg = 0.07f;
                    for (int i = 0; i < cn; i++) { cvx[i] = (cellCx(i) - mid) * 0.05f + Rng.r(1f); cvy[i] = -Rng.range(.5f, 2.6f); }
                    burst(70, mid, midY, 2.6f, 0.8f, 50, 3, 0xBDEFFF, 0.05f);
                    flash = 0.4f;
                }
                if (T <= 30 && ks % 4 == 0) sp(mid + Rng.r(50f), stTop + 20, Rng.r(.1f), .3f, 0, 80, 2, 0xFFFFFF);
            }
            case BLOOD -> {
                if (T <= 14 && ks % 2 == 0) sp(mid + Rng.r(30f), midY + Rng.r(30f), Rng.r(.3f), 0, 0.1f, 40, 2, 0x7A0A0A);
            }
            case NOVA -> {
                if (T <= 14 && ks % 2 == 0) {
                    float R = T * 5;
                    for (int i = 0, m = q(20); i < m; i++) {
                        float an = i * 6.283f / m;
                        float c = (float) Math.cos(an), s = (float) Math.sin(an);
                        sp(mid + c * R, midY + s * R * 0.9f, c * .8f, s * .8f, 0, 26, 3, 0x3DFFE0);
                    }
                }
                if (T >= 8 && stage == 0) {
                    stage = 1;
                    cd = 0.985f;
                    for (int i = 0; i < cn; i++) { cvx[i] = (cellCx(i) - mid) * 0.02f; cvy[i] = (cellCy(i) - midY) * 0.02f; }
                    flash = 0.6f;
                }
                if (T >= 4 && T <= 34 && ks % 2 == 0) sp(stL + Rng.f() * (stR - stL), ground - Rng.f() * 100, 0, -.3f, 0, 50, 2, 0x7CFFEE);
            }
            case GHOST -> {
                if (stage == 0) { stage = 1; flash = 0.7f; }
                if (ks % 2 == 0) sp(mid + Rng.r(16f), fig0y + Rng.f() * 32 * S, 0, -.4f, 0, 34, 2, Rng.pick(0xFFFFFF, 0xFF9AE6, 0xFFFFFF));
            }
            case BLACKHOLE -> {
                float py0 = headY - 16;
                if (T < 26) {
                    float k = Math.min(1f, T / 5f) * 0.045f;
                    for (int i = 0; i < cn; i++) { cvx[i] = (mid - cellCx(i)) * k; cvy[i] = (py0 - cellCy(i)) * k; }
                    if (ks % 2 == 0) {
                        float an = Rng.f() * 6.283f;
                        toward(mid + (float) Math.cos(an) * 70, py0 + (float) Math.sin(an) * 50, mid, py0, 24, 2, 0x7C3AED);
                    }
                }
                if (T >= 26 && stage == 0) {
                    stage = 1;
                    burst(90, mid, py0, 2.6f, 0, 45, 3, 0xE9D5FF, 0);
                    flash = 0.9f;
                    cd = 0; fadeS = ks; fadeE = ks + 4;
                    for (int i = 0; i < cn; i++) { cvx[i] = 0; cvy[i] = 0; }
                }
            }
            case SHATTER -> {
                if (T <= 10 && ks % 2 == 0) sp(mid + Rng.r(30f), ground - Rng.f() * 60, Rng.r(.2f), -.3f, 0, 40, 5, 0x9CA3AF);
            }
        }
    }

    // ---------------------------------------------------------------- рендер
    private static int argb(float a, int rgb) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private void line(DrawContext c, float x0, float y0, float x1, float y1, int sz, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        int n = (int) (Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(1, sz * 0.8f)) + 1;
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            int x = Math.round(x0 + dx * f), y = Math.round(y0 + dy * f);
            c.fill(x, y, x + sz, y + sz, col);
        }
    }

    void render(DrawContext c, Identifier skin) {
        if (!killed) return;
        float mid = stCx, midY = fig0y + 16 * S;
        float u = S / 3f;

        // --- сцена под телом
        switch (fx) {
            case GLACIAL -> {
                if (T < 24) {
                    float g = Math.min(1f, T / 6f);
                    for (int i = 0; i < 9; i++) {
                        float h = (22 + ((i * 37) % 5) * 8) * g * u;
                        int x0 = Math.round(mid + (i - 4) * 11 * u);
                        for (int yy = 0; yy < h; yy += 2) {
                            float wv = (1f - yy / Math.max(1f, h)) * 7f * u + 1;
                            c.fill(Math.round(x0 - wv / 2), Math.round(ground - yy), Math.round(x0 + wv / 2), Math.round(ground - yy + 2), argb(0.9f, yy % 4 == 0 ? 0xBDEFFF : 0x6FD3FF));
                        }
                    }
                }
            }
            case HELLFIRE -> {
                float R = Math.min(T * 3f, 55f);
                if (T < 30) for (int i = 0; i < 28; i++) {
                    float an = i * 6.283f / 28 + T * 0.3f;
                    int x = Math.round(mid + (float) Math.cos(an) * R), y = Math.round(ground + (float) Math.sin(an) * R * 0.25f);
                    c.fill(x, y, x + 3, y + 2, argb(0.9f, i % 2 == 0 ? 0xFF4500 : 0xFFB000));
                }
            }
            case BLOOD -> {
                if (T >= 3) {
                    float wv = Math.min(60f, (T - 3) * 6f);
                    for (int dy = -3; dy <= 3; dy++) {
                        float ww = wv * (1f - Math.abs(dy) / 4f);
                        c.fill(Math.round(mid - ww), Math.round(ground + dy), Math.round(mid + ww), Math.round(ground + dy + 1), argb(0.55f, 0x6A0808));
                    }
                }
            }
            default -> { }
        }

        // --- ячейки скина
        float al = 1f - Math.max(0f, Math.min(1f, (ks - fadeS) / Math.max(1f, fadeE - fadeS)));
        float sc = (fx == Effect.BLACKHOLE) ? 1f - Math.min(T / 26f, 1f) * 0.8f : 1f;
        if (al > 0.01f) {
            for (int i = 0; i < cn; i++) {
                int w = Math.max(1, Math.round(cw[i] * S * sc)), h = Math.max(1, Math.round(ch[i] * S * sc));
                int x = Math.round(cx[i]), y = Math.round(cy[i]);
                if (sil) {
                    c.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(al * 0.25f, 0xFFFFFF));
                    c.fill(x, y, x + w, y + h, argb(al * 0.9f, silCol));
                } else {
                    c.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, (float) cu[i], (float) cv[i], w, h,
                            cw[i], ch[i], 64, 64, argb(al, tint));
                }
            }
        }

        // --- частицы
        for (int i = 0; i < pn; i++) {
            int x = Math.round(px[i]), y = Math.round(py[i]);
            if (x < stL || x > stR || y < stTop || y > stBottom) continue;
            int s = Math.max(1, Math.round(ps[i]));
            c.fill(x, y, x + s, y + s, argb(Math.min(1f, pl[i] / Math.max(1f, pm[i]) * 1.6f), pc[i]));
        }

        // --- спецэлементы поверх
        switch (fx) {
            case SOUL_REAPER -> {
                if (T < 36) {
                    float g = Math.min(1f, T / 30f), oy = fig0y - 16;
                    int r = Math.round((4 + 8 * g) * u);
                    c.fill(Math.round(mid - r), Math.round(oy - r), Math.round(mid + r), Math.round(oy + r), argb(0.25f, 0x6D28D9));
                    r = Math.round(r * 0.65f);
                    c.fill(Math.round(mid - r), Math.round(oy - r), Math.round(mid + r), Math.round(oy + r), argb(0.55f, 0xA855F7));
                    r = Math.round(r * 0.5f);
                    c.fill(Math.round(mid - r), Math.round(oy - r), Math.round(mid + r), Math.round(oy + r), argb(0.95f, 0xF3E8FF));
                }
            }
            case ANGEL -> {
                float vis = Math.min(1f, T / 6f) * (1f - Math.max(0f, Math.min(1f, (T - 36f) / 12f)));
                if (vis > 0.02f) {
                    float grow = Math.min(1f, T / 10f);
                    float sy = fig0y + 9 * S;
                    float flap = (float) Math.sin(T * 0.5) * 6f;
                    for (int side = -1; side <= 1; side += 2) {
                        for (int row = 0; row < 5; row++) {
                            double ang = Math.toRadians(10 + row * 18 + flap);
                            float len = (46 - Math.abs(row - 2) * 8) * grow * u;
                            float x1 = mid + side * 5 * u + side * (float) Math.cos(ang) * len;
                            float y1 = sy - (float) Math.sin(ang) * len;
                            line(c, mid + side * 5 * u, sy, x1, y1, 2, argb(vis * 0.9f, row % 2 == 0 ? 0xFFF8DC : 0xFFE08A));
                        }
                    }
                    for (int i = 0; i < 16; i++) {
                        double an = i * 6.2832 / 16 + T * 0.2;
                        int x = Math.round(mid + (float) Math.cos(an) * 13 * u), y = Math.round(fig0y - 8 + (float) Math.sin(an) * 4 * u);
                        c.fill(x, y, x + 2, y + 2, argb(vis, 0xFFE08A));
                    }
                }
            }
            case THUNDER -> {
                if (T >= 6 && T <= 9.5f && bn > 1) {
                    for (int i = 0; i < bn - 1; i++) line(c, bx[i], by[i], bx[i + 1], by[i + 1], 4, argb(0.6f, 0x9FB4FF));
                    for (int i = 0; i < bn - 1; i++) line(c, bx[i], by[i], bx[i + 1], by[i + 1], 2, 0xFFFFFFFF);
                }
            }
            case NOVA -> {
                if (T < 36) {
                    float ph = T * 0.5f;
                    for (int ring = 0; ring < 2; ring++) {
                        float rx = (30 + ring * 12) * u, ry = ring == 0 ? 7 * u : 26 * u;
                        for (int i = 0; i < 12; i++) {
                            double an = (ring == 0 ? ph : -ph) + i * 6.2832 / 12;
                            int x = Math.round(mid + (float) Math.cos(an) * rx), y = Math.round(midY + (float) Math.sin(an) * ry);
                            c.fill(x, y, x + 3, y + 3, argb(0.9f, ring == 0 ? 0x3DFFE0 : 0xFFFFFF));
                        }
                    }
                }
            }
            case BLACKHOLE -> {
                if (T < 26) {
                    float oy = fig0y - 16;
                    float g = Math.min(1f, T / 6f);
                    for (int i = 0; i < 20; i++) {
                        double an = T * 0.8 + i * 6.2832 / 20;
                        int x = Math.round(mid + (float) Math.cos(an) * 20 * g * u), y = Math.round(oy + (float) Math.sin(an) * 6 * g * u);
                        c.fill(x, y, x + 2, y + 2, argb(0.95f, i % 2 == 0 ? 0xE9D5FF : 0x8B5CF6));
                    }
                    int r = Math.round(7 * g * u);
                    c.fill(Math.round(mid - r - 1), Math.round(oy - r - 1), Math.round(mid + r + 1), Math.round(oy + r + 1), argb(0.7f, 0x8B5CF6));
                    c.fill(Math.round(mid - r), Math.round(oy - r), Math.round(mid + r), Math.round(oy + r), 0xFF050008);
                }
            }
            default -> { }
        }

        // --- вспышка
        if (flash > 0f) {
            c.fill(Math.round(stL), Math.round(stTop), Math.round(stR), Math.round(stBottom), argb(flash * 0.7f, 0xFFFFFF));
        }
    }
}
