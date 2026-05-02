package dev.soffits.openplayer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OpenPlayerControlScreen extends Screen {
    private static final Component TITLE = Component.literal("OpenPlayer Controls");
    private static final Component SPAWN_BUTTON = Component.literal("Spawn NPC");
    private static final Component DESPAWN_BUTTON = Component.literal("Despawn NPC");
    private static final Component FOLLOW_OWNER_BUTTON = Component.literal("Follow Me");
    private static final Component STOP_BUTTON = Component.literal("Stop");
    private static final Component COMMAND_BUTTON = Component.literal("Send Command Text");
    private static final Component COMMAND_INPUT = Component.literal("Command or natural language input");
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;
    private static final int COMMAND_INPUT_WIDTH = 260;
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private EditBox commandInput;

    public OpenPlayerControlScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int left = (this.width - BUTTON_WIDTH) / 2;
        int top = (this.height - (BUTTON_HEIGHT * 6 + BUTTON_SPACING * 5)) / 2;
        this.addRenderableWidget(Button.builder(SPAWN_BUTTON, button -> OpenPlayerRequestSender.sendSpawnRequest())
                .bounds(left, top, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(DESPAWN_BUTTON, button -> OpenPlayerRequestSender.sendDespawnRequest())
                .bounds(left, top + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(FOLLOW_OWNER_BUTTON, button -> OpenPlayerRequestSender.sendFollowOwnerRequest())
                .bounds(left, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(STOP_BUTTON, button -> OpenPlayerRequestSender.sendStopRequest())
                .bounds(left, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        commandInput = new EditBox(
                this.font,
                (this.width - COMMAND_INPUT_WIDTH) / 2,
                top + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
                COMMAND_INPUT_WIDTH,
                BUTTON_HEIGHT,
                COMMAND_INPUT
        );
        commandInput.setMaxLength(MAX_COMMAND_TEXT_LENGTH);
        commandInput.setHint(COMMAND_INPUT);
        this.addRenderableWidget(commandInput);
        this.addRenderableWidget(Button.builder(COMMAND_BUTTON, button -> sendCommandText())
                .bounds(left, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 5, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        OpenPlayerRequestSender.sendStatusRequest();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int statusLeft = Math.max(12, (this.width - COMMAND_INPUT_WIDTH) / 2);
        int statusTop = this.height - 66;
        graphics.drawString(this.font, "Automation: " + OpenPlayerClientStatus.automationStatus(), statusLeft, statusTop, 0xC0C0C0);
        graphics.drawString(this.font, "Parser: " + OpenPlayerClientStatus.parserStatus(), statusLeft, statusTop + 12, 0xC0C0C0);
        graphics.drawString(this.font, "Endpoint: " + OpenPlayerClientStatus.endpointStatus(), statusLeft, statusTop + 24, 0xC0C0C0);
        graphics.drawString(this.font, "Model: " + OpenPlayerClientStatus.modelStatus(), statusLeft, statusTop + 36, 0xC0C0C0);
        graphics.drawString(this.font, "API key: " + OpenPlayerClientStatus.apiKeyStatus(), statusLeft, statusTop + 48, 0xC0C0C0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void sendCommandText() {
        String value = commandInput.getValue().trim();
        if (!value.isEmpty()) {
            OpenPlayerRequestSender.sendCommandTextRequest(value);
            commandInput.setValue("");
        }
    }
}
