package com.islandcraft;

/**
 * Central configuration for explosion behavior. Other systems can read/modify
 * these values at runtime to adjust explosion power/damage.
 */
public class ExplosionConfig {
    // Scale applied to requested explosion strength. Default 0.25 => 25% of base.
    public static volatile double EXPLOSION_POWER_SCALE = 0.25d;

    public static double getScale() {
        return EXPLOSION_POWER_SCALE;
    }

    public static void setScale(double s) {
        EXPLOSION_POWER_SCALE = s;
    }
}
