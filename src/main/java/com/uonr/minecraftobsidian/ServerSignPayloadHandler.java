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
            context.reply(new ObsidianSignPayloads.OperationResult(false, "message.minecraft_obsidian.link_failed"));
            return;
        }
        if (!ObsidianUrls.isObsidianUrl(payload.url())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "message.minecraft_obsidian.clipboard_not_obsidian"));
            return;
        }

        ServerSignLinkData data = ServerSignLinkData.get(player.server);
        boolean existed = data.get(payload.dimension(), payload.pos()).isPresent();
        data.put(payload.dimension(), payload.pos(), payload.url().trim());
        broadcastLinkUpdate((ServerLevel) player.level(), payload.dimension(), payload.pos(), true, payload.url().trim());
        context.reply(new ObsidianSignPayloads.OperationResult(true, existed ? "message.minecraft_obsidian.updated_server" : "message.minecraft_obsidian.linked_server"));
    }

    static void handleRemove(ObsidianSignPayloads.RemoveSign payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!isValidCurrentDimension(player, payload.dimension()) || !isNearby(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "message.minecraft_obsidian.remove_failed"));
            return;
        }
        if (isSign(player, payload.pos())) {
            context.reply(new ObsidianSignPayloads.OperationResult(false, "message.minecraft_obsidian.remove_sign_exists"));
            return;
        }

        boolean removed = ServerSignLinkData.get(player.server).remove(payload.dimension(), payload.pos());
        if (removed) {
            broadcastLinkUpdate((ServerLevel) player.level(), payload.dimension(), payload.pos(), false, "");
            context.reply(new ObsidianSignPayloads.OperationResult(true, "message.minecraft_obsidian.removed_server"));
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

        context.reply(new ObsidianSignPayloads.LinkedSignsSnapshot(payload.dimension(), ServerSignLinkData.get(player.server).linkedEntries(payload.dimension())));
    }

    static void broadcastLinkUpdate(ServerLevel level, ResourceLocation dimension, BlockPos pos, boolean linked, String url) {
        PacketDistributor.sendToPlayersInDimension(level, new ObsidianSignPayloads.LinkedSignUpdate(dimension, pos, linked, url));
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
