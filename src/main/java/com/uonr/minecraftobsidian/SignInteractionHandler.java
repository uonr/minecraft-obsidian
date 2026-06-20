package com.uonr.minecraftobsidian;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client-side sign behavior backed by per-vault {@code Minecraft Sign.md} mapping files.
 *
 * <ul>
 *   <li>Right-click a sign whose text is a known key: open the linked Obsidian note.</li>
 *   <li>Shift + right-click with an {@code obsidian://open?...} URL in the clipboard: bind the
 *       sign's text to that note (writes a wikilink entry into the owning vault's mapping file).</li>
 *   <li>Shift + place a sign with such a URL in the clipboard: bind automatically once the placed
 *       sign has text.</li>
 * </ul>
 */
final class SignInteractionHandler {
    private static final int SIGN_LINES = 4;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    // Window to wait for a freshly placed sign to gain text before auto-binding the clipboard URL.
    private static final int PLACEMENT_TIMEOUT_TICKS = 1200;

    private final SignNoteLinks links;
    private final List<PendingPlacement> pendingPlacements = new ArrayList<>();
    private PreviewState previewState;
    private int refreshTicks;

    SignInteractionHandler(SignNoteLinks links) {
        this.links = links;
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

        BlockPos pos = event.getPos();
        if (isSign(level, pos)) {
            MinecraftObsidianClient.LOGGER.debug("[mco] sign right-click at {} sneaking={} key={}",
                    pos, player.isShiftKeyDown(), signKey(level, pos).orElse("<none>"));
            links.refresh();
            if (tryBind(level, pos, player, minecraft) || tryOpen(level, pos, minecraft)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        schedulePlacementBind(level, event, player, minecraft);
    }

    /**
     * When a sign is being placed with an Obsidian URL on the clipboard, remember the candidate
     * positions and bind the link once the placed sign has text (i.e. after the edit screen closes).
     */
    private void schedulePlacementBind(ClientLevel level, PlayerInteractEvent.RightClickBlock event, Player player, Minecraft minecraft) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SignItem) || !player.isShiftKeyDown()) {
            return;
        }
        Optional<String> clipboard = ObsidianClipboard.readObsidianUrl(minecraft);
        if (clipboard.flatMap(ObsidianUrls::parseOpenTarget).isEmpty()) {
            return;
        }

        List<BlockPos> candidates = placementCandidates(level, event.getHitVec());
        if (!candidates.isEmpty()) {
            pendingPlacements.add(new PendingPlacement(candidates, clipboard.get()));
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            previewState = null;
            pendingPlacements.clear();
            return;
        }

        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            links.refresh();
        }
        processPendingPlacements(minecraft, level);
        previewState = createPreviewState(minecraft, level).orElse(null);
    }

    private void processPendingPlacements(Minecraft minecraft, ClientLevel level) {
        // While the sign edit screen is open its text updates live; wait until the player closes it
        // so we bind the finished text, not whatever was typed so far.
        if (minecraft.screen instanceof AbstractSignEditScreen) {
            return;
        }

        Iterator<PendingPlacement> iterator = pendingPlacements.iterator();
        while (iterator.hasNext()) {
            PendingPlacement pending = iterator.next();
            Optional<BlockPos> placed = pending.candidates.stream()
                    .filter(candidate -> signKey(level, candidate).isPresent())
                    .findFirst();
            if (placed.isPresent()) {
                links.refresh();
                performBind(minecraft, signKey(level, placed.get()).get(), pending.url);
                iterator.remove();
            } else if (pending.tickDownAndExpired()) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        BlockPos pos = event.getTarget().getBlockPos();
        Optional<String> key = signKey(level, pos);
        if (key.isEmpty() || !links.contains(key.get())) {
            return;
        }

        event.setCanceled(true);
        Vec3 camera = event.getCamera().getPosition();
        BlockState state = level.getBlockState(pos);
        AABB bounds = state.getShape(level, pos).isEmpty()
                ? new AABB(pos)
                : state.getShape(level, pos).bounds().move(pos);
        renderObsidianOutline(event, bounds.move(-camera.x, -camera.y, -camera.z));
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || previewState == null) {
            return;
        }
        renderPreviewPanel(event.getGuiGraphics(), minecraft.font, previewState);
    }

    private boolean tryBind(ClientLevel level, BlockPos pos, Player player, Minecraft minecraft) {
        if (!player.isShiftKeyDown()) {
            return false;
        }
        Optional<String> key = signKey(level, pos);
        Optional<String> clipboard = ObsidianClipboard.readObsidianUrl(minecraft);
        MinecraftObsidianClient.LOGGER.debug("[mco] bind check key={} clipboardObsidianUrl={}",
                key.orElse("<none>"), clipboard.orElse("<none>"));
        if (key.isEmpty() || clipboard.isEmpty()) {
            return false;
        }

        performBind(minecraft, key.get(), clipboard.get());
        return true;
    }

    private void performBind(Minecraft minecraft, String key, String url) {
        Optional<ObsidianUrls.OpenTarget> target = ObsidianUrls.parseOpenTarget(url);
        if (target.isEmpty()) {
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.bind_failed"), true);
            return;
        }

        SignNoteLinks.BindResult result = links.bind(key, target.get().vault(), target.get().file());
        MinecraftObsidianClient.LOGGER.debug("[mco] bind vault={} file={} -> success={} ambiguous={} vaultName={}",
                target.get().vault(), target.get().file(), result.success(), result.ambiguous(), result.vaultName());
        if (!result.success()) {
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.bind_failed"), true);
        } else if (result.ambiguous()) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.minecraft_obsidian.bind_ambiguous", result.vaultName()), false);
        } else {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.minecraft_obsidian.bound", result.vaultName()), true);
        }
    }

    private boolean tryOpen(ClientLevel level, BlockPos pos, Minecraft minecraft) {
        Optional<String> key = signKey(level, pos);
        if (key.isEmpty()) {
            return false;
        }
        Optional<SignNoteLinks.Resolution> resolution = links.resolve(key.get());
        if (resolution.isEmpty()) {
            return false;
        }

        String url = SignNoteLinks.openUrl(resolution.get());
        try {
            Util.getPlatform().openUri(url);
            minecraft.player.displayClientMessage(Component.translatable(resolution.get().ambiguous()
                    ? "message.minecraft_obsidian.open_ambiguous"
                    : "message.minecraft_obsidian.opened"), true);
        } catch (RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not open Obsidian URL {}", url, exception);
            minecraft.player.displayClientMessage(Component.translatable("message.minecraft_obsidian.open_failed"), false);
        }
        return true;
    }

    private Optional<PreviewState> createPreviewState(Minecraft minecraft, ClientLevel level) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        Optional<String> key = signKey(level, hit.getBlockPos());
        if (key.isEmpty()) {
            return Optional.empty();
        }

        String current = links.resolve(key.get())
                .map(resolution -> UrlPreview.fromUrl(SignNoteLinks.displayUrl(resolution)))
                .orElse(null);

        String update = null;
        if (minecraft.player.isShiftKeyDown()) {
            Optional<String> clipboard = ObsidianClipboard.readObsidianUrl(minecraft);
            if (clipboard.isPresent() && ObsidianUrls.parseOpenTarget(clipboard.get()).isPresent()) {
                String clipboardUrl = UrlPreview.fromUrl(clipboard.get());
                if (!clipboardUrl.equals(current)) {
                    update = clipboardUrl;
                }
            }
        }

        if (current == null && update == null) {
            return Optional.empty();
        }
        return Optional.of(new PreviewState(current, update));
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

    private static Optional<String> signKey(ClientLevel level, BlockPos pos) {
        if (!isSign(level, pos)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return Optional.empty();
        }

        SignText text = sign.getFrontText();
        String[] lines = new String[SIGN_LINES];
        for (int i = 0; i < SIGN_LINES; i++) {
            lines[i] = text.getMessage(i, false).getString();
        }
        String key = SignKey.fromLines(lines);
        return key.isEmpty() ? Optional.empty() : Optional.of(key);
    }

    private static boolean isSign(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SignBlock;
    }

    private static void renderObsidianOutline(RenderHighlightEvent.Block event, AABB bounds) {
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        // Layered boxes make the outline read thicker while staying in vanilla's line renderer.
        LevelRenderer.renderLineBox(event.getPoseStack(), buffer, bounds.inflate(0.004D), 0.47F, 0.18F, 0.95F, 1.0F);
        LevelRenderer.renderLineBox(event.getPoseStack(), buffer, bounds.inflate(0.010D), 0.64F, 0.30F, 1.0F, 0.92F);
        LevelRenderer.renderLineBox(event.getPoseStack(), buffer, bounds.inflate(0.016D), 0.24F, 0.04F, 0.55F, 0.82F);
    }

    private static void renderPreviewPanel(GuiGraphics gui, Font font, PreviewState state) {
        int maxWidth = Math.min((int) (gui.guiWidth() * 0.72F), 420);
        int contentWidth = Math.max(120, maxWidth - 20);

        String current = state.current() == null ? null : fitMiddle(font, state.current(), contentWidth);
        String update = null;
        if (state.update() != null) {
            String prefix = Component.translatable("message.minecraft_obsidian.preview_update_prefix").getString();
            update = prefix + fitMiddle(font, state.update(), Math.max(20, contentWidth - font.width(prefix)));
        }

        int panelWidth = 0;
        if (current != null) {
            panelWidth = font.width(current);
        }
        if (update != null) {
            panelWidth = Math.max(panelWidth, font.width(update));
        }
        panelWidth = Math.min(panelWidth + 20, maxWidth);

        int lines = (current != null ? 1 : 0) + (update != null ? 1 : 0);
        int panelHeight = lines <= 1 ? 22 : 34;
        int x = (gui.guiWidth() - panelWidth) / 2;
        int y = Math.max(12, gui.guiHeight() / 2 + 24);

        gui.fill(x, y, x + panelWidth, y + panelHeight, 0xD00B0613);
        gui.fill(x, y, x + panelWidth, y + 1, 0xFF8B5CFF);
        gui.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF3B0A74);
        gui.fill(x, y, x + 1, y + panelHeight, 0xFF6E32D8);
        gui.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, 0xFF6E32D8);

        int textY = y + 7;
        if (current != null) {
            gui.drawString(font, current, x + 10, textY, 0xFFE9DDFF, false);
            textY += 12;
        }
        if (update != null) {
            gui.drawString(font, update, x + 10, textY, 0xFFBFA7FF, false);
        }
    }

    private static String fitMiddle(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }

        String marker = "...";
        int markerWidth = font.width(marker);
        if (markerWidth >= maxWidth) {
            return marker;
        }

        int leftLength = Math.max(1, value.length() / 2);
        int rightLength = Math.max(1, value.length() - leftLength);
        while (leftLength + rightLength > 2) {
            String candidate = value.substring(0, leftLength) + marker + value.substring(value.length() - rightLength);
            if (font.width(candidate) <= maxWidth) {
                return candidate;
            }
            if (leftLength > rightLength && leftLength > 1) {
                leftLength--;
            } else if (rightLength > 1) {
                rightLength--;
            } else {
                leftLength--;
            }
        }
        return marker;
    }

    private record PreviewState(String current, String update) {
    }

    private static final class PendingPlacement {
        private final List<BlockPos> candidates;
        private final String url;
        private int ticksRemaining = PLACEMENT_TIMEOUT_TICKS;

        PendingPlacement(List<BlockPos> candidates, String url) {
            this.candidates = List.copyOf(candidates);
            this.url = url;
        }

        boolean tickDownAndExpired() {
            return --ticksRemaining <= 0;
        }
    }
}
