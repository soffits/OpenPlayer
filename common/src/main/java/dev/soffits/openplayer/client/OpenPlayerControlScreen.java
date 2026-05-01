package dev.soffits.openplayer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OpenPlayerControlScreen extends Screen {
    private static final Component TITLE = Component.literal("OpenPlayer Controls");
    private static final Component SPAWN_BUTTON = Component.literal("Spawn NPC");
    private static final Component DESPAWN_BUTTON = Component.literal("Despawn NPC");
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;

    public OpenPlayerControlScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int left = (this.width - BUTTON_WIDTH) / 2;
        int top = (this.height - (BUTTON_HEIGHT * 2 + BUTTON_SPACING)) / 2;
        this.addRenderableWidget(Button.builder(SPAWN_BUTTON, button -> OpenPlayerRequestSender.sendSpawnRequest())
                .bounds(left, top, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(DESPAWN_BUTTON, button -> OpenPlayerRequestSender.sendDespawnRequest())
                .bounds(left, top + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
