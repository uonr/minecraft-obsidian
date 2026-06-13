package com.uonr.minecraftobsidian;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

final class ServerSignLinkData extends SavedData {
    private static final String DATA_NAME = ModConstants.MODID + "_sign_links";
    private static final String LINKS_TAG = "links";
    private static final Factory<ServerSignLinkData> FACTORY = new Factory<>(ServerSignLinkData::new, ServerSignLinkData::load);

    private final Map<String, String> links = new LinkedHashMap<>();

    static ServerSignLinkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ServerSignLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerSignLinkData data = new ServerSignLinkData();
        CompoundTag linksTag = tag.getCompound(LINKS_TAG);
        for (String key : linksTag.getAllKeys()) {
            data.links.put(key, linksTag.getString(key));
        }
        return data;
    }

    Optional<String> get(ResourceLocation dimension, BlockPos pos) {
        return Optional.ofNullable(links.get(key(dimension, pos)));
    }

    List<ObsidianSignPayloads.LinkedSignEntry> linkedEntries(ResourceLocation dimension) {
        String prefix = dimension + "|";
        return links.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> parseEntry(entry, prefix.length()))
                .flatMap(Optional::stream)
                .toList();
    }

    void put(ResourceLocation dimension, BlockPos pos, String url) {
        links.put(key(dimension, pos), url);
        setDirty();
    }

    boolean remove(ResourceLocation dimension, BlockPos pos) {
        if (links.remove(key(dimension, pos)) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag linksTag = new CompoundTag();
        links.forEach(linksTag::putString);
        tag.put(LINKS_TAG, linksTag);
        return tag;
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Optional<BlockPos> parsePos(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ObsidianSignPayloads.LinkedSignEntry> parseEntry(Map.Entry<String, String> entry, int prefixLength) {
        return parsePos(entry.getKey().substring(prefixLength))
                .map(pos -> new ObsidianSignPayloads.LinkedSignEntry(pos, entry.getValue()));
    }
}
