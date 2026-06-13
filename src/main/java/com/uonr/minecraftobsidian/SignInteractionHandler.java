package com.uonr.minecraftobsidian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class SignInteractionHandler {
    private static final int CONFIRM_TICKS = 8;
    private static final int REMOVAL_CONFIRM_TICKS = 80;

    private final SignLinkStore store;
    private final List<PendingPlacement> pendingPlacements = new ArrayList<>();
    private final List<PendingRemoval> pendingRemovals = new ArrayList<>();
    private final Set<BlockPos> serverLinkedSigns = new HashSet<>();
    private StorageMode storageMode = StorageMode.UNKNOWN;
    private ResourceLocation requestedSnapshotDimension;

    SignInteractionHandler(SignLinkStore store) {
        this.store = store;
        ClientSignPayloadHandler.setSignInteractionHandler(this);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = event.getEntity();
        if (level == null || minecraft.player == null || player != minecraft.player) {
            return;
        }
        updateStorageMode(minecraft, false);

        String worldId = currentWorldId(minecraft);
        String dimensionId = dimensionId(level.dimension());
        ResourceLocation dimension = level.dimension().location();
        BlockPos clickedPos = event.getPos();

        if (tryOpenLinkedSign(level, worldId, dimensionId, dimension, clickedPos, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SignItem)) {
            return;
        }

        Optional<String> url = ObsidianClipboard.readObsidianUrl(minecraft);
        if (url.isEmpty()) {
            return;
        }

        List<BlockPos> candidates = placementCandidates(level, event.getHitVec());
        if (!candidates.isEmpty()) {
            pendingPlacements.add(new PendingPlacement(worldId, dimensionId, candidates, url.get()));
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = event.getEntity();
        if (level == null || minecraft.player == null || player != minecraft.player) {
            return;
        }
        updateStorageMode(minecraft, false);

        BlockPos pos = event.getPos();
        String worldId = currentWorldId(minecraft);
        String dimensionId = dimensionId(level.dimension());
        if (isSign(level, pos) && (isServerBacked() || store.get(worldId, dimensionId, pos).isPresent())) {
            addOrRefreshPendingRemoval(worldId, dimensionId, pos);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            pendingPlacements.clear();
            pendingRemovals.clear();
            storageMode = StorageMode.UNKNOWN;
            return;
        }
        updateStorageMode(minecraft, true);
        if (pendingPlacements.isEmpty() && pendingRemovals.isEmpty()) {
            return;
        }

        String worldId = currentWorldId(minecraft);
        String dimensionId = dimensionId(level.dimension());
        processPendingPlacements(minecraft, level, worldId, dimensionId);
        processPendingRemovals(minecraft, level, worldId, dimensionId);
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingPlacements.clear();
        pendingRemovals.clear();
        serverLinkedSigns.clear();
        requestedSnapshotDimension = null;
        storageMode = StorageMode.UNKNOWN;
    }

    private void processPendingPlacements(Minecraft minecraft, ClientLevel level, String worldId, String dimensionId) {
        Iterator<PendingPlacement> iterator = pendingPlacements.iterator();
        while (iterator.hasNext()) {
            PendingPlacement pending = iterator.next();
            if (!pending.matches(worldId, dimensionId)) {
                iterator.remove();
                continue;
            }

            Optional<BlockPos> placedSign = pending.findPlacedSign(level);
            if (placedSign.isPresent()) {
                BlockPos pos = placedSign.get();
                if (isServerBacked()) {
                    PacketDistributor.sendToServer(new ObsidianSignPayloads.BindSign(level.dimension().location(), pos, pending.url()));
                } else {
                    store.put(worldId, dimensionId, pos, pending.url());
                    minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.linked_local"), true);
                }
                iterator.remove();
                continue;
            }

            if (pending.tickDownAndExpired()) {
                iterator.remove();
            }
        }
    }

    private void processPendingRemovals(Minecraft minecraft, ClientLevel level, String worldId, String dimensionId) {
        Iterator<PendingRemoval> iterator = pendingRemovals.iterator();
        while (iterator.hasNext()) {
            PendingRemoval pending = iterator.next();
            if (!pending.matches(worldId, dimensionId)) {
                iterator.remove();
                continue;
            }

            if (!isSign(level, pending.pos())) {
                if (isServerBacked()) {
                    PacketDistributor.sendToServer(new ObsidianSignPayloads.RemoveSign(level.dimension().location(), pending.pos()));
                } else {
                    store.remove(worldId, dimensionId, pending.pos());
                    minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.removed_local"), true);
                }
                iterator.remove();
                continue;
            }

            if (pending.tickDownAndExpired()) {
                iterator.remove();
            }
        }
    }

    private void addOrRefreshPendingRemoval(String worldId, String dimensionId, BlockPos pos) {
        for (PendingRemoval pending : pendingRemovals) {
            if (pending.matches(worldId, dimensionId) && pending.pos().equals(pos)) {
                pending.refresh();
                return;
            }
        }
        pendingRemovals.add(new PendingRemoval(worldId, dimensionId, pos));
    }

    private boolean tryOpenLinkedSign(ClientLevel level, String worldId, String dimensionId, ResourceLocation dimension, BlockPos pos, Player player) {
        if (!isSign(level, pos)) {
            if (!isServerBacked()) {
                store.remove(worldId, dimensionId, pos);
            }
            return false;
        }

        if (isServerBacked()) {
            if (!serverLinkedSigns.contains(pos)) {
                return false;
            }
            PacketDistributor.sendToServer(new ObsidianSignPayloads.OpenSign(dimension, pos));
            return true;
        }

        Optional<String> url = store.get(worldId, dimensionId, pos);
        if (url.isEmpty()) {
            return false;
        }

        try {
            Util.getPlatform().openUri(url.get());
            player.displayClientMessage(Component.translatable("message.minecraft_obsidian.opened"), true);
        } catch (RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not open Obsidian URL {}", url.get(), exception);
            player.displayClientMessage(Component.translatable("message.minecraft_obsidian.open_failed"), false);
        }
        return true;
    }

    private void updateStorageMode(Minecraft minecraft, boolean announce) {
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }

        StorageMode newMode = minecraft.getConnection().hasChannel(ObsidianSignPayloads.BindSign.TYPE) ? StorageMode.SERVER : StorageMode.LOCAL;
        if (newMode == storageMode) {
            if (newMode == StorageMode.SERVER) {
                requestLinkedSignsSnapshot(minecraft);
            }
            return;
        }

        storageMode = newMode;
        serverLinkedSigns.clear();
        requestedSnapshotDimension = null;
        if (announce) {
            String messageKey = switch (storageMode) {
                case SERVER -> "message.minecraft_obsidian.mode_server";
                case LOCAL -> "message.minecraft_obsidian.mode_local";
                case UNKNOWN -> "";
            };
            if (!messageKey.isEmpty()) {
                minecraft.player.displayClientMessage(Component.translatable(messageKey), false);
            }
        }
        if (storageMode == StorageMode.SERVER) {
            requestLinkedSignsSnapshot(minecraft);
        }
    }

    private boolean isServerBacked() {
        return storageMode == StorageMode.SERVER;
    }

    void handleLinkedSignsSnapshot(ObsidianSignPayloads.LinkedSignsSnapshot payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) {
            return;
        }
        serverLinkedSigns.clear();
        payload.positions().forEach(pos -> serverLinkedSigns.add(pos.immutable()));
        requestedSnapshotDimension = payload.dimension();
    }

    void handleLinkedSignUpdate(ObsidianSignPayloads.LinkedSignUpdate payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) {
            return;
        }
        if (payload.linked()) {
            serverLinkedSigns.add(payload.pos().immutable());
        } else {
            serverLinkedSigns.remove(payload.pos());
        }
    }

    private void requestLinkedSignsSnapshot(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (dimension.equals(requestedSnapshotDimension)) {
            return;
        }
        requestedSnapshotDimension = dimension;
        PacketDistributor.sendToServer(new ObsidianSignPayloads.RequestLinkedSigns(dimension));
    }

    private static List<BlockPos> placementCandidates(ClientLevel level, BlockHitResult hit) {
        List<BlockPos> candidates = new ArrayList<>(2);
        addCandidateIfNotSign(level, candidates, hit.getBlockPos());
        addCandidateIfNotSign(level, candidates, hit.getBlockPos().relative(hit.getDirection()));
        return candidates;
    }

    private static void addCandidateIfNotSign(ClientLevel level, List<BlockPos> candidates, BlockPos pos) {
        if (!isSign(level, pos) && !candidates.contains(pos)) {
            candidates.add(pos.immutable());
        }
    }

    private static boolean isSign(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SignBlock;
    }

    private static String currentWorldId(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip;
        }
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    private enum StorageMode {
        UNKNOWN,
        LOCAL,
        SERVER
    }

    private static final class PendingPlacement {
        private final String worldId;
        private final String dimensionId;
        private final List<BlockPos> candidates;
        private final String url;
        private int ticksRemaining;

        PendingPlacement(String worldId, String dimensionId, List<BlockPos> candidates, String url) {
            this.worldId = worldId;
            this.dimensionId = dimensionId;
            this.candidates = List.copyOf(candidates);
            this.url = url;
            this.ticksRemaining = CONFIRM_TICKS;
        }

        String url() {
            return url;
        }

        boolean matches(String currentWorldId, String currentDimensionId) {
            return worldId.equals(currentWorldId) && dimensionId.equals(currentDimensionId);
        }

        Optional<BlockPos> findPlacedSign(ClientLevel level) {
            return candidates.stream().filter(pos -> isSign(level, pos)).findFirst();
        }

        boolean tickDownAndExpired() {
            ticksRemaining--;
            return ticksRemaining <= 0;
        }
    }

    private static final class PendingRemoval {
        private final String worldId;
        private final String dimensionId;
        private final BlockPos pos;
        private int ticksRemaining;

        PendingRemoval(String worldId, String dimensionId, BlockPos pos) {
            this.worldId = worldId;
            this.dimensionId = dimensionId;
            this.pos = pos.immutable();
            this.ticksRemaining = REMOVAL_CONFIRM_TICKS;
        }

        BlockPos pos() {
            return pos;
        }

        boolean matches(String currentWorldId, String currentDimensionId) {
            return worldId.equals(currentWorldId) && dimensionId.equals(currentDimensionId);
        }

        void refresh() {
            ticksRemaining = REMOVAL_CONFIRM_TICKS;
        }

        boolean tickDownAndExpired() {
            ticksRemaining--;
            return ticksRemaining <= 0;
        }
    }
}
