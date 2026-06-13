package com.uonr.minecraftobsidian;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ClientSignPayloadHandler {
    private static SignInteractionHandler signInteractionHandler;

    private ClientSignPayloadHandler() {
    }

    static void setSignInteractionHandler(SignInteractionHandler handler) {
        signInteractionHandler = handler;
    }

    static void handleOpenSignResponse(ObsidianSignPayloads.OpenSignResponse payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (!payload.found()) {
            minecraft.player.displayClientMessage(Component.literal("No Obsidian link found for this sign"), true);
            return;
        }
        String url = payload.url().trim();
        if (!ObsidianUrls.isObsidianUrl(url)) {
            MinecraftObsidianClient.LOGGER.warn("Server sent non-Obsidian URL: {}", payload.url());
            minecraft.player.displayClientMessage(Component.literal("Server sent an invalid Obsidian link"), false);
            return;
        }

        try {
            Util.getPlatform().openUri(url);
            minecraft.player.displayClientMessage(Component.literal("Opened Obsidian link"), true);
        } catch (RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not open Obsidian URL {}", url, exception);
            minecraft.player.displayClientMessage(Component.literal("Could not open Obsidian link"), false);
        }
    }

    static void handleOperationResult(ObsidianSignPayloads.OperationResult payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !payload.message().isBlank()) {
            minecraft.player.displayClientMessage(Component.literal(payload.message()), !payload.success());
        }
    }

    static void handleLinkedSignsSnapshot(ObsidianSignPayloads.LinkedSignsSnapshot payload) {
        if (signInteractionHandler != null) {
            signInteractionHandler.handleLinkedSignsSnapshot(payload);
        }
    }

    static void handleLinkedSignUpdate(ObsidianSignPayloads.LinkedSignUpdate payload) {
        if (signInteractionHandler != null) {
            signInteractionHandler.handleLinkedSignUpdate(payload);
        }
    }
}
