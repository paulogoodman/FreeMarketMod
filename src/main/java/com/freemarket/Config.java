package com.freemarket;

import net.neoforged.neoforge.common.ModConfigSpec;

// Configuration class for FreeMarket mod
// Demonstrates NeoForge config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();


    public static final ModConfigSpec.ConfigValue<String> MARKETPLACE_NAME = BUILDER
            .comment("The display name for the marketplace GUI")
            .define("marketplaceName", "Free Market");

    static final ModConfigSpec SPEC = BUILDER.build();

}
