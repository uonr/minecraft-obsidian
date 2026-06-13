package com.uonr.minecraftobsidian;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MinecraftObsidianClient.MODID, dist = Dist.CLIENT)
public class MinecraftObsidianClient {
    public static final String MODID = ModConstants.MODID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftObsidianClient() {
        SignLinkStore store = new SignLinkStore(MODID);
        NeoForge.EVENT_BUS.register(new SignInteractionHandler(store));
    }
}
