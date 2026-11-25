package dev.kak1x.fx;

/** Быстрый xorshift без аллокаций (клиентский поток однопоточный). */
final class Rng {
    private static int s = 0x9E3779B9;

    private Rng() {}

    static int i() {
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        return s;
    }

    /** [0,1) */
    static float f() {
        return (i() >>> 8) * (1f / 16777216f);
    }

    /** [-a,a] */
    static float r(float a) {
        return (f() * 2f - 1f) * a;
    }

    static float range(float a, float b) {
        return a + (b - a) * f();
    }

    static int pick(int a, int b, int c) {
        float v = f();
        return v < 0.34f ? a : (v < 0.67f ? b : c);
    }
}
