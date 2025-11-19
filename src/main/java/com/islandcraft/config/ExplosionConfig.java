package com.islandcraft.config;

/**
 * Simple static configuration holder for explosion scaling.
 *
 * This is intentionally a minimal static holder so the value can be
 * referenced from server-side logic and updated later (or wired into a
 * proper Forge config) when you want to expose it to users.
 */
public final class ExplosionConfig {
    private ExplosionConfig() {
    }

    /**
     * Scale applied to requested explosion strength. Default 0.25 (25%).
     * Example: base 100 -> scaled 25 when set to 0.25.
     */
    public static double EXPLOSION_SCALE = 0.25d;

    public static float scaledStrength(float baseStrength) {
        return (float) (baseStrength * EXPLOSION_SCALE);
    }
}
