package com.uonr.minecraftobsidian;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Discovers the user's Obsidian vaults from Obsidian's own registry file so signs do not need to
 * carry a vault name. The vault {@code id} is stable across renames and is what we hand back to
 * {@code obsidian://} URLs.
 */
final class ObsidianVaults {
    private ObsidianVaults() {
    }

    /** Location of Obsidian's global {@code obsidian.json} registry for the current platform. */
    static Path configFile() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", "");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null && !appData.isBlank()
                    ? Path.of(appData)
                    : Path.of(userHome, "AppData", "Roaming");
            return base.resolve("obsidian").resolve("obsidian.json");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(userHome, "Library", "Application Support", "obsidian", "obsidian.json");
        }

        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(userHome, ".config");
        return base.resolve("obsidian").resolve("obsidian.json");
    }

    static List<ObsidianVault> discover() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            MinecraftObsidianClient.LOGGER.info("[mco] obsidian.json not found at {}", file);
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || !root.has("vaults") || !root.get("vaults").isJsonObject()) {
                return List.of();
            }

            JsonObject vaults = root.getAsJsonObject("vaults");
            List<ObsidianVault> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : vaults.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                if (!value.has("path") || value.get("path").isJsonNull()) {
                    continue;
                }
                String pathValue = value.get("path").getAsString();
                if (pathValue == null || pathValue.isBlank()) {
                    continue;
                }
                Path vaultRoot = Path.of(pathValue);
                Path name = vaultRoot.getFileName();
                result.add(new ObsidianVault(entry.getKey(), name == null ? pathValue : name.toString(), vaultRoot));
            }
            MinecraftObsidianClient.LOGGER.info("[mco] discovered {} vault(s) from {}: {}",
                    result.size(), file, result.stream().map(ObsidianVault::name).toList());
            return result;
        } catch (Exception exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not read Obsidian vault registry at {}", file, exception);
            return List.of();
        }
    }
}
