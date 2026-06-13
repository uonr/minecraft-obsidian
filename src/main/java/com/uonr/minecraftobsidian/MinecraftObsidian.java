package com.uonr.minecraftobsidian;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ModConstants.MODID)
public class MinecraftObsidian {
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftObsidian(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.register(new ServerSignEvents());
    }
}
