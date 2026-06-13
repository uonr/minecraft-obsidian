package com.uonr.minecraftobsidian;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MinecraftObsidianClient.MODID, dist = Dist.CLIENT)
public class MinecraftObsidianClient {
    public static final String MODID = ModConstants.MODID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftObsidianClient(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new ObsidianConfigScreen(parent));
        SignLinkStore store = new SignLinkStore(MODID);
        NeoForge.EVENT_BUS.register(new SignInteractionHandler(store));
    }
}
