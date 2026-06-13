package com.uonr.minecraftobsidian;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

final class ServerSignLinks {
    private ServerSignLinks() {
    }

    static Optional<String> get(ServerLevel level, BlockPos pos) {
        return sign(level, pos).flatMap(ServerSignLinks::get);
    }

    static Optional<String> get(SignBlockEntity sign) {
        return sign.getExistingData(ModAttachments.SIGN_URL.get())
                .map(String::trim)
                .filter(ObsidianUrls::isObsidianUrl);
    }

    static Optional<Boolean> put(ServerLevel level, BlockPos pos, String url) {
        Optional<SignBlockEntity> sign = sign(level, pos);
        if (sign.isEmpty()) {
            return Optional.empty();
        }

        boolean existed = get(sign.get()).isPresent();
        sign.get().setData(ModAttachments.SIGN_URL.get(), url.trim());
        sign.get().setChanged();
        return Optional.of(existed);
    }

    static boolean remove(ServerLevel level, BlockPos pos) {
        Optional<SignBlockEntity> sign = sign(level, pos);
        if (sign.isEmpty()) {
            return false;
        }

        String removed = sign.get().removeData(ModAttachments.SIGN_URL.get());
        if (removed == null) {
            return false;
        }
        sign.get().setChanged();
        return !removed.trim().isEmpty();
    }

    static List<ObsidianSignPayloads.LinkedSignEntry> linkedEntries(ServerPlayer player, ResourceLocation dimension) {
        if (!player.level().dimension().location().equals(dimension)) {
            return List.of();
        }

        ServerLevel level = player.serverLevel();
        List<ObsidianSignPayloads.LinkedSignEntry> entries = new ArrayList<>();
        player.getChunkTrackingView().forEach(chunkPos -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                return;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof SignBlockEntity sign) {
                    get(sign).ifPresent(url -> entries.add(new ObsidianSignPayloads.LinkedSignEntry(sign.getBlockPos().immutable(), url)));
                }
            }
        });
        return entries;
    }

    private static Optional<SignBlockEntity> sign(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            return Optional.of(sign);
        }
        return Optional.empty();
    }
}
