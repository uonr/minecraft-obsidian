package com.uonr.minecraftobsidian;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.core.BlockPos;

final class SignLinkStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LINKS_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final String modId;
    private final Map<String, String> links = new LinkedHashMap<>();
    private Path loadedFile;
    private boolean loaded;

    SignLinkStore(String modId) {
        this.modId = modId;
    }

    Optional<String> get(String worldId, String dimensionId, BlockPos pos) {
        ensureLoaded();
        return Optional.ofNullable(links.get(key(worldId, dimensionId, pos)));
    }

    void put(String worldId, String dimensionId, BlockPos pos, String url) {
        ensureLoaded();
        links.put(key(worldId, dimensionId, pos), url);
        save();
    }

    void remove(String worldId, String dimensionId, BlockPos pos) {
        ensureLoaded();
        if (links.remove(key(worldId, dimensionId, pos)) != null) {
            save();
        }
    }

    List<BlockPos> positions(String worldId, String dimensionId) {
        ensureLoaded();
        List<BlockPos> positions = new ArrayList<>();
        String prefix = worldId + "|" + dimensionId + "|";
        for (String key : links.keySet()) {
            if (key.startsWith(prefix)) {
                parsePosition(key.substring(prefix.length())).ifPresent(positions::add);
            }
        }
        return positions;
    }

    private static String key(String worldId, String dimensionId, BlockPos pos) {
        return worldId + "|" + dimensionId + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Optional<BlockPos> parsePosition(String value) {
        String[] parts = value.split(",", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private void ensureLoaded() {
        Path file = ClientConfig.localLinkFile(modId);
        if (!file.equals(loadedFile)) {
            links.clear();
            loaded = false;
            loadedFile = file;
        }
        if (loaded) {
            return;
        }
        loaded = true;

        if (!Files.isRegularFile(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> loadedLinks = GSON.fromJson(reader, LINKS_TYPE);
            if (loadedLinks != null) {
                links.putAll(loadedLinks);
            }
        } catch (IOException | JsonSyntaxException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not load Obsidian sign links from {}", file, exception);
        }
    }

    private void save() {
        Path file = ClientConfig.localLinkFile(modId);
        if (!file.equals(loadedFile)) {
            links.clear();
            loaded = false;
            loadedFile = file;
            ensureLoaded();
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(links, LINKS_TYPE, writer);
            }
        } catch (IOException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not save Obsidian sign links to {}", file, exception);
        }
    }
}
