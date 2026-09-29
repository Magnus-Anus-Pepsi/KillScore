package com.example.killscore;

import com.example.killscore.network.Network;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(KillScoreMod.MOD_ID)
public class KillScoreMod {
    public static final String MOD_ID = "killscore";

    public KillScoreMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KillScoreConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, KillScoreConfig.CLIENT_SPEC);
        Network.register();
    }
}
