package com.uonr.minecraftobsidian;

import java.util.Optional;

import net.minecraft.client.Minecraft;

final class ObsidianClipboard {
    private ObsidianClipboard() {
    }

    static Optional<String> readObsidianUrl(Minecraft minecraft) {
        String clipboard;
        try {
            clipboard = minecraft.keyboardHandler.getClipboard();
        } catch (RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.debug("Could not read clipboard", exception);
            return Optional.empty();
        }

        if (clipboard == null) {
            return Optional.empty();
        }

        String trimmed = clipboard.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        return ObsidianUrls.isObsidianUrl(trimmed) ? Optional.of(trimmed) : Optional.empty();
    }
}
