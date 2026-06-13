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
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.no_link"), true);
            return;
        }
        String url = payload.url().trim();
        if (!ObsidianUrls.isObsidianUrl(url)) {
            MinecraftObsidianClient.LOGGER.warn("Server sent non-Obsidian URL: {}", payload.url());
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.invalid_server_url"), false);
            return;
        }

        try {
            Util.getPlatform().openUri(url);
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.opened"), true);
        } catch (RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not open Obsidian URL {}", url, exception);
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.open_failed"), false);
        }
    }

    static void handleOperationResult(ObsidianSignPayloads.OperationResult payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !payload.message().isBlank()) {
            minecraft.player.displayClientMessage(Component.translatable(payload.message()), !payload.success());
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
