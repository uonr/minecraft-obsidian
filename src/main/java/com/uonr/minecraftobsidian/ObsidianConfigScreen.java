package com.uonr.minecraftobsidian;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ObsidianConfigScreen extends Screen {
    private static final int FIELD_WIDTH = 360;
    private static final int BUTTON_WIDTH = 98;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private EditBox localLinkFile;
    private Component status = CommonComponents.EMPTY;

    ObsidianConfigScreen(Screen parent) {
        super(Component.translatable("config.minecraft_obsidian.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(FIELD_WIDTH, this.width - 40);
        int centerX = this.width / 2;
        int fieldY = this.height / 2 - 28;

        this.localLinkFile = new EditBox(
                this.font,
                centerX - fieldWidth / 2,
                fieldY,
                fieldWidth,
                20,
                Component.translatable("config.minecraft_obsidian.local_link_file"));
        this.localLinkFile.setMaxLength(1024);
        this.localLinkFile.setValue(ClientConfig.localLinkFileValue());
        this.localLinkFile.setHint(Component.translatable("config.minecraft_obsidian.local_link_file.hint"));
        this.addRenderableWidget(this.localLinkFile);
        this.setInitialFocus(this.localLinkFile);

        int buttonY = Math.min(this.height - 32, fieldY + 62);
        int gap = 6;
        int totalWidth = BUTTON_WIDTH * 3 + gap * 2;
        int startX = centerX - totalWidth / 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.minecraft_obsidian.reset"), button -> {
            ClientConfig.resetLocalLinkFileValue();
            this.localLinkFile.setValue("");
            this.status = Component.translatable("config.minecraft_obsidian.saved");
        }).bounds(startX + BUTTON_WIDTH + gap, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(startX + (BUTTON_WIDTH + gap) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        int textX = this.localLinkFile.getX();
        int labelY = this.localLinkFile.getY() - 12;
        gui.drawString(this.font, Component.translatable("config.minecraft_obsidian.local_link_file"), textX, labelY, 0xFFE0E0E0, false);
        gui.drawWordWrap(
                this.font,
                Component.translatable("config.minecraft_obsidian.local_link_file.help"),
                textX,
                this.localLinkFile.getY() + 25,
                this.localLinkFile.getWidth(),
                0xFFB8B8B8);
        if (!this.status.equals(CommonComponents.EMPTY)) {
            gui.drawCenteredString(this.font, this.status, this.width / 2, Math.min(this.height - 48, this.height / 2 + 84), 0xFF80E08A);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void saveAndClose() {
        ClientConfig.setLocalLinkFileValue(this.localLinkFile.getValue());
        onClose();
    }
}
