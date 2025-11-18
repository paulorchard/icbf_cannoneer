package com.islandcraft.client;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {

    public static final ForgeConfigSpec.BooleanValue SHOW_IN_THIRD_PERSON;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Client settings for ICBF Cannoneer");
        SHOW_IN_THIRD_PERSON = builder
                .comment(
                        "When true, the spyglass face-highlighting overlay will be shown when the camera is in third-person mode")
                .define("showInThirdPerson", false);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
