package com.uonr.minecraftobsidian;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

final class ServerSignEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && event.getState().getBlock() instanceof SignBlock) {
            if (ServerSignLinkData.get(level.getServer()).remove(level.dimension().location(), event.getPos())) {
                ServerSignPayloadHandler.broadcastLinkUpdate(level, level.dimension().location(), event.getPos(), false);
            }
        }
    }
}
