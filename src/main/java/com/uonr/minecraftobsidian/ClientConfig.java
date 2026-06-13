package com.uonr.minecraftobsidian;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

final class ClientConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> LOCAL_LINK_FILE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        LOCAL_LINK_FILE = builder
                .comment(
                        "Optional path for local fallback sign links.",
                        "Leave empty to use config/minecraft_obsidian/links.json.",
                        "Relative paths are resolved from the Minecraft game directory.")
                .translation("config.minecraft_obsidian.local_link_file")
                .define("localLinkFile", "", ClientConfig::isValidPathValue);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    static Path localLinkFile(String modId) {
        String configured = LOCAL_LINK_FILE.get().trim();
        if (configured.isEmpty()) {
            return defaultLocalLinkFile(modId);
        }

        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return FMLPaths.GAMEDIR.get().resolve(path).normalize();
    }

    static String localLinkFileValue() {
        return LOCAL_LINK_FILE.get();
    }

    static void setLocalLinkFileValue(String value) {
        LOCAL_LINK_FILE.set(value.trim());
        SPEC.save();
    }

    static void resetLocalLinkFileValue() {
        LOCAL_LINK_FILE.set("");
        SPEC.save();
    }

    static Path defaultLocalLinkFile(String modId) {
        return FMLPaths.CONFIGDIR.get().resolve(modId).resolve("links.json");
    }

    private static boolean isValidPathValue(Object value) {
        if (!(value instanceof String path)) {
            return false;
        }
        try {
            if (!path.trim().isEmpty()) {
                Path.of(path.trim());
            }
            return true;
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
