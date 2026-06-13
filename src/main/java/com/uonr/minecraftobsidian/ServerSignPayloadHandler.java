package com.uonr.minecraftobsidian;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class ServerSignPayloadHandler {
    private static final double MAX_INTERACTION_DISTANCE = 8.0D;

    private ServerSignPayloadHandler() {
    }

    static void handleBind(ObsidianSignPayloads.BindSign payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!isValidCurrentDimension(player, payload.dimension()) || !isNearby(player, payload.pos()) || !isSign(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "Could not link sign"));
            return;
        }
        if (!ObsidianUrls.isObsidianUrl(payload.url())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "Clipboard is not an Obsidian URL"));
            return;
        }

        ServerSignLinkData.get(player.server).put(payload.dimension(), payload.pos(), payload.url().trim());
        broadcastLinkUpdate((ServerLevel) player.level(), payload.dimension(), payload.pos(), true);
        context.reply(new ObsidianSignPayloads.OperationResult(true, "Linked sign to Obsidian URL on server"));
    }

    static void handleRemove(ObsidianSignPayloads.RemoveSign payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!isValidCurrentDimension(player, payload.dimension()) || !isNearby(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "Could not remove sign link"));
            return;
        }
        if (isSign(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "Cannot remove link while sign still exists"));
            return;
        }

        boolean removed = ServerSignLinkData.get(player.server).remove(payload.dimension(), payload.pos());
        if (removed) {
            broadcastLinkUpdate((ServerLevel) player.level(), payload.dimension(), payload.pos(), false);
            context.reply(new ObsidianSignPayloads.OperationResult(true, "Removed Obsidian sign link from server"));
        }
    }

    static void handleOpen(ObsidianSignPayloads.OpenSign payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!isValidCurrentDimension(player, payload.dimension()) || !isNearby(player, payload.pos()) || !isSign(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OpenSignResponse(payload.dimension(), payload.pos(), false, ""));
            return;
        }

        String url = ServerSignLinkData.get(player.server).get(payload.dimension(), payload.pos()).orElse("");
        context.reply(new ObsidianSignPayloads.OpenSignResponse(payload.dimension(), payload.pos(), !url.isEmpty(), url));
    }

    static void handleRequestLinkedSigns(ObsidianSignPayloads.RequestLinkedSigns payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!isValidCurrentDimension(player, payload.dimension())) {
            context.reply(new ObsidianSignPayloads.LinkedSignsSnapshot(payload.dimension(), java.util.List.of()));
            return;
        }

        context.reply(new ObsidianSignPayloads.LinkedSignsSnapshot(payload.dimension(), ServerSignLinkData.get(player.server).linkedPositions(payload.dimension())));
    }

    static void broadcastLinkUpdate(ServerLevel level, ResourceLocation dimension, BlockPos pos, boolean linked) {
        PacketDistributor.sendToPlayersInDimension(level, new ObsidianSignPayloads.LinkedSignUpdate(dimension, pos, linked));
    }

    private static boolean isValidCurrentDimension(ServerPlayer player, ResourceLocation dimension) {
        return player.level().dimension().location().equals(dimension);
    }

    private static boolean isNearby(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().closerThan(pos, MAX_INTERACTION_DISTANCE);
    }

    private static boolean isSign(ServerPlayer player, BlockPos pos) {
        return player.level().getBlockState(pos).getBlock() instanceof SignBlock;
    }
}
