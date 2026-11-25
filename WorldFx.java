package dev.kak1x.fx;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;

/**
 * Эффекты в мире. Оптимизации:
 *  - ноль работы, когда нет активных эффектов;
 *  - отсечение по дистанции, лимит одновременных эффектов;
 *  - бюджет частиц зависит от качества, числа активных эффектов и расстояния;
 *  - позиционная анимация (без лишних аллокаций, константные ParticleEffect);
 *  - собственный xorshift вместо ThreadLocalRandom.
 */
final class WorldFx {
    private WorldFx() {}

    static final class Active {
        Effect e;
        double x, y, z, h;
        int age;
        float mult;
        double fx, fz; // от жертвы к камере
        double rx, rz; // «вправо» относительно камеры
    }

    private static final ArrayList<Active> ACTIVE = new ArrayList<>(8);
    private static final double[] BP = new double[3];

    // ---- константные эффекты частиц ----
    private static final DustParticleEffect PURPLE = new DustParticleEffect(0xA855F7, 1.3f);
    private static final DustParticleEffect PURPLE_BIG = new DustParticleEffect(0xC084FC, 2.4f);
    private static final DustParticleEffect DPURPLE = new DustParticleEffect(0x6D28D9, 1.1f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(0xFFE08A, 1.2f);
    private static final DustParticleEffect WHITE = new DustParticleEffect(0xFFFFFF, 1.1f);
    private static final DustParticleEffect GRAY = new DustParticleEffect(0x4A4F5C, 3.0f);
    private static final DustParticleEffect ORANGE = new DustParticleEffect(0xFF7A18, 1.3f);
    private static final DustParticleEffect YELLOW = new DustParticleEffect(0xFFD166, 1.1f);
    private static final DustParticleEffect ICE = new DustParticleEffect(0x9FE8FF, 1.2f);
    private static final DustParticleEffect RED = new DustParticleEffect(0xC81414, 1.4f);
    private static final DustParticleEffect DRED = new DustParticleEffect(0x6A0808, 1.2f);
    private static final DustParticleEffect CYAN = new DustParticleEffect(0x3DFFE0, 1.2f);
    private static final DustParticleEffect PINK = new DustParticleEffect(0xFF6FD8, 1.3f);
    private static final DustParticleEffect PINK_W = new DustParticleEffect(0xFFD0F4, 1.0f);
    private static final DustParticleEffect VOID = new DustParticleEffect(0x1A0033, 1.6f);
    private static final DustParticleEffect VIOLET = new DustParticleEffect(0x8B5CF6, 1.2f);

    private static final BlockStateParticleEffect ICE_B =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLUE_ICE.getDefaultState());
    private static final BlockStateParticleEffect ICE_P =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState());
    private static final BlockStateParticleEffect BLOOD_B =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect[] PIXELS = {
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CYAN_CONCRETE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PURPLE_CONCRETE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PINK_CONCRETE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.getDefaultState()),
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_CONCRETE.getDefaultState())
    };

    static boolean idle() {
        return ACTIVE.isEmpty();
    }

    static void clear() {
        ACTIVE.clear();
    }

    // ---- запуск ----
    static void start(Effect e, ClientWorld w, double x, double y, double z, double h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config c = Config.I;
        if (mc.player == null) return;
        double d2 = mc.player.squaredDistanceTo(x, y, z);
        double md = c.distance;
        if (d2 > md * md) return;                 // слишком далеко: ничего не рисуем
        if (ACTIVE.size() >= c.maxActive) return; // лимит одновременных эффектов

        Active a = new Active();
        a.e = e;
        a.x = x;
        a.y = y;
        a.z = z;
        a.h = Math.max(h, 0.6);
        float m = c.qmul();
        if (ACTIVE.size() >= 3) m *= 0.6f;
        double half = md * 0.5;
        if (d2 > half * half) m *= 0.6f;          // далеко = меньше частиц
        a.mult = m;

        double dx = mc.player.getX() - x, dz = mc.player.getZ() - z;
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-3) {
            dx = 0;
            dz = 1;
            l = 1;
        }
        a.fx = dx / l;
        a.fz = dz / l;
        a.rx = a.fz;
        a.rz = -a.fx;
        ACTIVE.add(a);
    }

    static void tick(ClientWorld w) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Active a = ACTIVE.get(i);
            Snd.schedule(a.e, a.age, a.x, a.y + a.h * 0.5, a.z, false);
            step(w, a);
            if (++a.age > a.e.ticks) {
                int last = ACTIVE.size() - 1;
                ACTIVE.set(i, ACTIVE.get(last));
                ACTIVE.remove(last);
            }
        }
    }

    // ---- помощники ----
    private static void p(ClientWorld w, ParticleEffect e, double x, double y, double z,
                          double vx, double vy, double vz) {
        w.addParticleClient(e, x, y, z, vx, vy, vz);
    }

    private static int n(Active a, int base) {
        int v = Math.round(base * a.mult);
        return v < 1 ? 1 : v;
    }

    private static void sph(ClientWorld w, ParticleEffect e, double cx, double cy, double cz, double r) {
        double x = Rng.r(1f), y = Rng.r(1f), z = Rng.r(1f);
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-4) {
            l = 1;
            x = 1;
        }
        double k = r / l;
        p(w, e, cx + x * k, cy + y * k, cz + z * k, 0, 0, 0);
    }

    /** Случайная точка внутри «человечка» (голова/тело/руки/ноги), лицом к камере. */
    private static void body(Active a) {
        double s = a.h / 1.8;
        float u = Rng.f();
        double bx, by, bz;
        if (u < 0.18f) {
            bx = Rng.r(.22f);
            by = Rng.range(1.4f, 1.85f);
            bz = Rng.r(.22f);
        } else if (u < 0.5f) {
            bx = Rng.r(.25f);
            by = Rng.range(.7f, 1.4f);
            bz = Rng.r(.13f);
        } else if (u < 0.7f) {
            bx = (Rng.f() < .5f ? -1 : 1) * Rng.range(.3f, .5f);
            by = Rng.range(.7f, 1.4f);
            bz = Rng.r(.13f);
        } else {
            bx = (Rng.f() < .5f ? -1 : 1) * Rng.range(.02f, .25f);
            by = Rng.range(0f, .7f);
            bz = Rng.r(.13f);
        }
        BP[0] = a.x + a.rx * bx * s + a.fx * bz * s;
        BP[1] = a.y + by * s;
        BP[2] = a.z + a.rz * bx * s + a.fz * bz * s;
    }

    // ---- анимации ----
    private static void step(ClientWorld w, Active a) {
        final int t = a.age;
        final double cy = a.y + a.h * 0.5;

        switch (a.e) {
            case SOUL_REAPER -> {
                double ox = a.x, oy = a.y + a.h + 1.2, oz = a.z;
                if (t == 0) {
                    for (int i = 0, m = n(a, 40); i < m; i++) {
                        body(a);
                        p(w, PURPLE, BP[0], BP[1], BP[2], Rng.r(.1f), .05, Rng.r(.1f));
                    }
                }
                if (t <= 30) {
                    double k = 1.0 - t / 30.0;
                    for (int i = 0, m = n(a, 6); i < m; i++) {
                        double an = Rng.f() * 6.2832, r = 0.4 + k * 1.8;
                        p(w, DPURPLE, ox + Math.cos(an) * r, oy + Rng.r(.9f) * k, oz + Math.sin(an) * r, 0, 0, 0);
                    }
                    for (int i = 0, m = n(a, 3); i < m; i++) {
                        p(w, PURPLE_BIG, ox + Rng.r(.18f), oy + Rng.r(.18f), oz + Rng.r(.18f), 0, 0, 0);
                    }
                    if ((t & 1) == 0) p(w, ParticleTypes.SOUL, a.x + Rng.r(.3f), a.y + t * 0.1, a.z + Rng.r(.3f), 0, .05, 0);
                }
                if (t == 31) {
                    for (int i = 0, m = n(a, 70); i < m; i++) sph(w, PURPLE, ox, oy, oz, Rng.range(.3f, 1.4f));
                    for (int i = 0, m = n(a, 16); i < m; i++) p(w, ParticleTypes.SCULK_SOUL, ox, oy, oz, Rng.r(.2f), Rng.r(.2f), Rng.r(.2f));
                }
            }
            case ANGEL -> {
                double sy = a.y + a.h * 0.78;
                float grow = Math.min(1f, (t + 1) / 10f);
                if (t <= 28 && (t & 1) == 0) {
                    int pts = Math.max(3, n(a, 6));
                    for (int side = -1; side <= 1; side += 2) {
                        for (int row = 0; row < 5; row++) {
                            double ang = Math.toRadians(15 + row * 20 + Math.sin(t * 0.5) * 6);
                            double len = (1.8 - Math.abs(row - 2) * 0.3) * grow;
                            for (int k = 1; k <= pts; k++) {
                                double s = k / (double) pts * len;
                                double lat = side * Math.cos(ang) * s;
                                double up = Math.sin(ang) * s;
                                double back = 0.25 + 0.1 * s;
                                double px = a.x + a.rx * lat - a.fx * back;
                                double py = sy + up;
                                double pz = a.z + a.rz * lat - a.fz * back;
                                p(w, (k == pts) ? ParticleTypes.END_ROD : (row % 2 == 0 ? GOLD : WHITE), px, py, pz, 0, 0, 0);
                            }
                        }
                    }
                }
                if (t <= 30) {
                    double hy = a.y + a.h + 0.35 + Math.min(t, 20) * 0.02;
                    for (int i = 0, m = Math.max(6, n(a, 14)); i < m; i++) {
                        double an = i * 6.2832 / m + t * 0.2;
                        p(w, GOLD, a.x + Math.cos(an) * 0.35, hy, a.z + Math.sin(an) * 0.35, 0, 0, 0);
                    }
                    for (int i = 0, m = n(a, 2); i < m; i++) {
                        p(w, ParticleTypes.END_ROD, a.x + Rng.r(.35f), a.y + t * 0.12, a.z + Rng.r(.35f), 0, .04, 0);
                    }
                }
                if (t >= 8 && t <= 40) {
                    for (int i = 0, m = n(a, 2); i < m; i++) {
                        p(w, ParticleTypes.SNOWFLAKE, a.x + Rng.r(1.4f), a.y + a.h + 1.0 + Rng.f(), a.z + Rng.r(1.4f), 0, -.02, 0);
                    }
                }
                if (t == 30) {
                    for (int i = 0, m = n(a, 24); i < m; i++) {
                        p(w, ParticleTypes.END_ROD, a.x, a.y + a.h + 0.3, a.z, Rng.r(.2f), Rng.range(0f, .25f), Rng.r(.2f));
                    }
                }
            }
            case THUNDER -> {
                double cyc = a.y + a.h + 3.2;
                if (t <= 6) {
                    for (int i = 0, m = n(a, 14); i < m; i++)
                        p(w, ParticleTypes.LARGE_SMOKE, a.x + Rng.r(1.6f), cyc + Rng.r(.5f), a.z + Rng.r(1.6f), 0, 0, 0);
                    for (int i = 0, m = n(a, 8); i < m; i++)
                        p(w, GRAY, a.x + Rng.r(1.6f), cyc + Rng.r(.4f), a.z + Rng.r(1.6f), 0, 0, 0);
                }
                if (t == 6) {
                    double bx = a.x + Rng.r(.2f), bz = a.z + Rng.r(.2f);
                    for (double yy = cyc; yy > a.y; yy -= 0.35) {
                        bx += Rng.r(.3f);
                        bz += Rng.r(.3f);
                        p(w, ParticleTypes.END_ROD, bx, yy, bz, 0, 0, 0);
                        p(w, ParticleTypes.ELECTRIC_SPARK, bx + Rng.r(.2f), yy, bz + Rng.r(.2f), Rng.r(.2f), Rng.r(.1f), Rng.r(.2f));
                    }
                    for (int i = 0, m = n(a, 60); i < m; i++)
                        p(w, ParticleTypes.ELECTRIC_SPARK, a.x, cy, a.z, Rng.r(.7f), Rng.range(0f, .5f), Rng.r(.7f));
                    for (int i = 0, m = Math.max(8, n(a, 24)); i < m; i++) {
                        double an = i * 6.2832 / m;
                        p(w, ParticleTypes.CLOUD, a.x + Math.cos(an) * .8, a.y + .1, a.z + Math.sin(an) * .8, Math.cos(an) * .25, .02, Math.sin(an) * .25);
                    }
                }
                if (t > 6 && t <= 12) {
                    for (int i = 0, m = n(a, 10); i < m; i++) {
                        body(a);
                        p(w, ParticleTypes.ELECTRIC_SPARK, BP[0], BP[1], BP[2], Rng.r(.2f), Rng.r(.2f), Rng.r(.2f));
                    }
                }
            }
            case HELLFIRE -> {
                double r = 0.4 + t * 0.12;
                int m = Math.max(8, n(a, 24));
                for (int i = 0; i < m; i++) {
                    double an = i * 6.2832 / m + t * 0.3;
                    p(w, ParticleTypes.FLAME, a.x + Math.cos(an) * r, a.y + .1, a.z + Math.sin(an) * r, 0, .02, 0);
                }
                for (int i = 0, k = n(a, 10); i < k; i++) {
                    double an = t * 0.6 + i;
                    p(w, (i & 1) == 0 ? ORANGE : YELLOW, a.x + Math.cos(an) * 0.7, a.y + Rng.f() * a.h, a.z + Math.sin(an) * 0.7, 0, 0, 0);
                }
                if (t == 0) {
                    for (int i = 0, k = n(a, 40); i < k; i++)
                        p(w, ParticleTypes.FLAME, a.x, cy, a.z, Rng.r(.35f), Rng.range(0f, .3f), Rng.r(.35f));
                }
                if (t % 4 == 0) p(w, ParticleTypes.LAVA, a.x + Rng.r(.6f), a.y + 1, a.z + Rng.r(.6f), 0, 0, 0);
                if (t == 8) {
                    for (int i = 0, k = Math.max(8, n(a, 28)); i < k; i++) {
                        double an = i * 6.2832 / k;
                        p(w, ParticleTypes.LARGE_SMOKE, a.x + Math.cos(an) * 2.6, a.y + .2, a.z + Math.sin(an) * 2.6, Math.cos(an) * .1, .02, Math.sin(an) * .1);
                    }
                }
            }
            case GLACIAL -> {
                if (t <= 14) {
                    double grow = Math.min(1.0, (t + 1) / 8.0);
                    for (int s = 0; s < 12; s++) {
                        double an = s * 0.5236 + (s % 2) * 0.2;
                        double rr = 0.3 + (s % 3) * 0.35;
                        double len = (1.0 + ((s * 7) % 5) * 0.35) * grow;
                        double sx = a.x + Math.cos(an) * rr, sz = a.z + Math.sin(an) * rr;
                        int pts = Math.max(2, (int) (len / 0.3 * a.mult) + 1);
                        for (int q = 0; q < pts; q++) {
                            double f = q / (double) pts;
                            p(w, ICE, sx - Math.cos(an) * rr * 0.4 * f, a.y + len * f, sz - Math.sin(an) * rr * 0.4 * f, 0, 0, 0);
                        }
                        if ((t & 1) == 0) p(w, ParticleTypes.SNOWFLAKE, sx, a.y + len, sz, 0, 0, 0);
                    }
                }
                if (t == 0) {
                    for (int i = 0, m = n(a, 25); i < m; i++)
                        p(w, ICE_B, a.x + Rng.r(.8f), a.y + .1, a.z + Rng.r(.8f), Rng.r(.15f), Rng.range(.05f, .25f), Rng.r(.15f));
                }
                if (t == 14) {
                    for (int i = 0, m = n(a, 70); i < m; i++) {
                        body(a);
                        p(w, (i & 1) == 0 ? ICE_B : ICE_P, BP[0], BP[1], BP[2], Rng.r(.35f), Rng.r(.3f) + .1, Rng.r(.35f));
                    }
                    for (int i = 0, m = n(a, 40); i < m; i++)
                        p(w, ParticleTypes.SNOWFLAKE, a.x + Rng.r(1.2f), cy + Rng.r(.8f), a.z + Rng.r(1.2f), Rng.r(.15f), Rng.r(.15f), Rng.r(.15f));
                }
            }
            case BLOOD -> {
                if (t == 0) {
                    for (int i = 0, m = n(a, 90); i < m; i++) {
                        body(a);
                        p(w, (i % 3 == 0) ? DRED : RED, BP[0], BP[1], BP[2], Rng.r(.4f), Rng.r(.3f) + .1, Rng.r(.4f));
                    }
                    for (int i = 0, m = n(a, 35); i < m; i++) {
                        body(a);
                        p(w, BLOOD_B, BP[0], BP[1], BP[2], Rng.r(.3f), Rng.range(0f, .3f), Rng.r(.3f));
                    }
                    for (int i = 0, m = n(a, 8); i < m; i++)
                        p(w, ParticleTypes.DAMAGE_INDICATOR, a.x, cy + .3, a.z, Rng.r(.3f), Rng.r(.1f), Rng.r(.3f));
                }
                if (t <= 6) {
                    double r = 0.3 + t * 0.25;
                    for (int i = 0, m = Math.max(6, n(a, 12)); i < m; i++) {
                        double an = i * 6.2832 / m + t;
                        p(w, DRED, a.x + Math.cos(an) * r, a.y + .05, a.z + Math.sin(an) * r, 0, 0, 0);
                    }
                    for (int i = 0, m = n(a, 5); i < m; i++) {
                        body(a);
                        p(w, RED, BP[0], BP[1], BP[2], 0, -.05, 0);
                    }
                }
            }
            case NOVA -> {
                if (t <= 12) {
                    double r = 0.2 + t * 0.22;
                    for (int i = 0, m = n(a, 45); i < m; i++) sph(w, CYAN, a.x, cy, a.z, r);
                }
                double ph = t * 0.5;
                int m = Math.max(6, n(a, 12));
                for (int ring = 0; ring < 2; ring++) {
                    double rr = 1.1 + ring * 0.4;
                    for (int i = 0; i < m; i++) {
                        double an = ph * (ring == 0 ? 1 : -1) + i * 6.2832 / m;
                        double c = Math.cos(an) * rr, s = Math.sin(an) * rr;
                        if (ring == 0) {
                            p(w, CYAN, a.x + c, cy + s * 0.15, a.z + s, 0, 0, 0);
                        } else {
                            // кольцо, наклонённое «в камеру»
                            p(w, ParticleTypes.END_ROD, a.x + a.rx * c, cy + s * 0.8, a.z + a.rz * c, 0, 0, 0);
                        }
                    }
                }
                if (t >= 4 && t <= 28) {
                    for (int i = 0, k = n(a, 8); i < k; i++)
                        p(w, ParticleTypes.GLOW, a.x + Rng.r(3.5f), a.y + Rng.f() * 2.2, a.z + Rng.r(3.5f), 0, .02, 0);
                }
                if (t == 0) {
                    for (int i = 0, k = n(a, 30); i < k; i++)
                        p(w, ParticleTypes.END_ROD, a.x, cy, a.z, Rng.r(.3f), Rng.r(.3f), Rng.r(.3f));
                }
            }
            case GHOST -> {
                if (t == 0) {
                    for (int i = 0, m = Math.max(8, n(a, 24)); i < m; i++) {
                        double an = i * 6.2832 / m;
                        p(w, ParticleTypes.END_ROD, a.x + Math.cos(an) * 1.2, cy, a.z + Math.sin(an) * 1.2, Math.cos(an) * .08, 0, Math.sin(an) * .08);
                    }
                    for (int i = 0, m = n(a, 12); i < m; i++)
                        p(w, ParticleTypes.GLOW, a.x, cy, a.z, Rng.r(.2f), Rng.r(.2f), Rng.r(.2f));
                }
                double lift = t * 0.045;
                for (int i = 0, m = n(a, 26); i < m; i++) {
                    body(a);
                    p(w, (i & 1) == 0 ? PINK : PINK_W, BP[0], BP[1] + lift, BP[2], 0, 0.03, 0);
                }
                if (t > 8 && (t & 1) == 0)
                    p(w, ParticleTypes.END_ROD, a.x + Rng.r(.3f), a.y + a.h + t * 0.05, a.z + Rng.r(.3f), 0, .03, 0);
            }
            case BLACKHOLE -> {
                double px = a.x, py = a.y + a.h + 0.6, pz = a.z;
                if (t < 20) {
                    double k = 1 - t / 20.0;
                    for (int i = 0, m = n(a, 20); i < m; i++) sph(w, (i & 1) == 0 ? VOID : VIOLET, px, py, pz, 0.4 + 2.2 * k);
                    int rm = Math.max(8, n(a, 16));
                    for (int i = 0; i < rm; i++) {
                        double an = t * 0.8 + i * 6.2832 / rm;
                        double c = Math.cos(an), s = Math.sin(an);
                        p(w, ParticleTypes.END_ROD, px + a.rx * c, py + s * 0.35, pz + a.rz * c, 0, 0, 0);
                    }
                    for (int i = 0, m = n(a, 6); i < m; i++) p(w, VOID, px + Rng.r(.15f), py + Rng.r(.15f), pz + Rng.r(.15f), 0, 0, 0);
                    // тело затягивает в дыру
                    for (int i = 0, m = n(a, 6); i < m; i++) {
                        body(a);
                        double f = t / 20.0;
                        p(w, VIOLET, BP[0] + (px - BP[0]) * f, BP[1] + (py - BP[1]) * f, BP[2] + (pz - BP[2]) * f, 0, 0, 0);
                    }
                }
                if (t == 20) {
                    for (int i = 0, m = n(a, 90); i < m; i++) sph(w, (i & 1) == 0 ? WHITE : VIOLET, px, py, pz, Rng.range(.3f, 2.2f));
                    p(w, ParticleTypes.SONIC_BOOM, px, py, pz, 0, 0, 0);
                }
            }
            case SHATTER -> {
                if (t == 0) {
                    for (int i = 0, m = n(a, 110); i < m; i++) {
                        body(a);
                        BlockStateParticleEffect pe = PIXELS[(Rng.i() >>> 3) % PIXELS.length];
                        p(w, pe, BP[0], BP[1], BP[2], (BP[0] - a.x) * 0.3 + Rng.r(.2f), Rng.range(0f, .4f), (BP[2] - a.z) * 0.3 + Rng.r(.2f));
                    }
                    for (int i = 0, m = n(a, 10); i < m; i++) p(w, ParticleTypes.SMOKE, a.x + Rng.r(.4f), cy + Rng.r(.6f), a.z + Rng.r(.4f), 0, .02, 0);
                }
                if (t <= 6) {
                    for (int i = 0, m = n(a, 12); i < m; i++) {
                        body(a);
                        p(w, WHITE, BP[0], BP[1], BP[2], Rng.r(.15f), Rng.r(.1f), Rng.r(.15f));
                    }
                }
            }
        }
    }
}
