package io.github.mrxsin.ytmprobe.target;

/**
 * Harmless hook targets. Unhooked, both return their input unchanged.
 *
 * <p>A probe generation G hooking H1 returns {@code x + G * 1000}; hooking H2 returns
 * {@code x + G * 1000 + 500}. Any other value is an invalid half-state.</p>
 */
public final class ProbeTarget {

    private int calls;

    public int h1(int x) {
        calls++;
        return x;
    }

    public int h2(int x) {
        calls++;
        return x;
    }

    public int calls() {
        return calls;
    }
}
