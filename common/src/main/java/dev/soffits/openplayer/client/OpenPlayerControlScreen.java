package dev.soffits.openplayer.client;

import dev.soffits.openplayer.character.LocalCharacterListEntry;
import java.util.ArrayList;
import java.util.List;
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
    private static final int BUTTON_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 6;
    private static final int COMMAND_INPUT_WIDTH = 220;
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final int MAX_VISIBLE_CHARACTERS = 6;
    private EditBox commandInput;
    private String selectedCharacterId;
    private String renderedCharacterKey = "";

    public OpenPlayerControlScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        rebuildControlWidgets();
        OpenPlayerRequestSender.sendStatusRequest();
        OpenPlayerRequestSender.sendCharacterListRequest();
    }

    @Override
    public void tick() {
        String key = characterKey();
        if (!key.equals(renderedCharacterKey)) {
            String commandText = commandInput == null ? "" : commandInput.getValue();
            rebuildControlWidgets();
            commandInput.setValue(commandText);
        }
    }

    private void rebuildControlWidgets() {
        this.clearWidgets();
        renderedCharacterKey = characterKey();
        int margin = 12;
        int listWidth = Math.min(180, Math.max(118, this.width / 3));
        int rightLeft = margin + listWidth + 12;
        int rightWidth = Math.max(160, this.width - rightLeft - margin);
        int controlsLeft = rightLeft + Math.max(0, (rightWidth - BUTTON_WIDTH) / 2);
        int top = Math.max(44, this.height / 2 - 72);

        List<LocalCharacterListEntry> characters = OpenPlayerClientStatus.characters();
        for (int index = 0; index < Math.min(MAX_VISIBLE_CHARACTERS, characters.size()); index++) {
            LocalCharacterListEntry character = characters.get(index);
            int y = 58 + index * (BUTTON_HEIGHT + 3);
            this.addRenderableWidget(Button.builder(Component.literal(character.displayName()), button -> selectedCharacterId = character.id())
                    .bounds(margin, y, listWidth, BUTTON_HEIGHT)
                    .build());
        }

        this.addRenderableWidget(Button.builder(SPAWN_BUTTON, button -> sendSpawn())
                .bounds(controlsLeft, top, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(DESPAWN_BUTTON, button -> sendDespawn())
                .bounds(controlsLeft, top + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(FOLLOW_OWNER_BUTTON, button -> sendFollow())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(STOP_BUTTON, button -> sendStop())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        commandInput = new EditBox(
                this.font,
                rightLeft + Math.max(0, (rightWidth - COMMAND_INPUT_WIDTH) / 2),
                top + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
                Math.min(COMMAND_INPUT_WIDTH, rightWidth),
                BUTTON_HEIGHT,
                COMMAND_INPUT
        );
        commandInput.setMaxLength(MAX_COMMAND_TEXT_LENGTH);
        commandInput.setHint(COMMAND_INPUT);
        this.addRenderableWidget(commandInput);
        this.addRenderableWidget(Button.builder(COMMAND_BUTTON, button -> sendCommandText())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 5, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int margin = 12;
        int listWidth = Math.min(180, Math.max(118, this.width / 3));
        int rightLeft = margin + listWidth + 12;
        graphics.drawString(this.font, "Characters: " + OpenPlayerClientStatus.characterListStatus(), margin, 42, 0xFFFFFF);
        renderCharacterMessages(graphics, margin, 58 + Math.min(MAX_VISIBLE_CHARACTERS, OpenPlayerClientStatus.characters().size()) * 23, listWidth);
        renderSelectedDetails(graphics, rightLeft, 42, Math.max(150, this.width - rightLeft - margin));
        int statusLeft = rightLeft;
        int statusTop = this.height - 66;
        graphics.drawString(this.font, "Automation: " + OpenPlayerClientStatus.automationStatus(), statusLeft, statusTop, 0xC0C0C0);
        graphics.drawString(this.font, "Parser: " + OpenPlayerClientStatus.parserStatus(), statusLeft, statusTop + 12, 0xC0C0C0);
        graphics.drawString(this.font, "Endpoint: " + OpenPlayerClientStatus.endpointStatus(), statusLeft, statusTop + 24, 0xC0C0C0);
        graphics.drawString(this.font, "Model: " + OpenPlayerClientStatus.modelStatus(), statusLeft, statusTop + 36, 0xC0C0C0);
        graphics.drawString(this.font, "API key: " + OpenPlayerClientStatus.apiKeyStatus(), statusLeft, statusTop + 48, 0xC0C0C0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCharacterMessages(GuiGraphics graphics, int left, int top, int width) {
        if (OpenPlayerClientStatus.characters().isEmpty()) {
            graphics.drawString(this.font, "No local characters found.", left, top, 0xA0A0A0);
        }
        int y = top + 14;
        for (String error : OpenPlayerClientStatus.characterErrors()) {
            graphics.drawString(this.font, fit(error, width), left, y, 0xFF8888);
            y += 12;
        }
    }

    private void renderSelectedDetails(GuiGraphics graphics, int left, int top, int width) {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            graphics.drawString(this.font, "Selected: default spawn path", left, top, 0xFFFFFF);
            graphics.drawString(this.font, "Choose a character to target actions.", left, top + 14, 0xA0A0A0);
            return;
        }
        graphics.drawString(this.font, "Selected: " + fit(selected.displayName(), width - 58), left, top, 0xFFFFFF);
        graphics.drawString(this.font, "ID: " + selected.id(), left, top + 14, 0xC0C0C0);
        graphics.drawString(this.font, "Description: " + fit(selected.description().isEmpty() ? "none" : selected.description(), width - 70), left, top + 28, 0xC0C0C0);
        graphics.drawString(this.font, "Skin: " + selected.skinStatus(), left, top + 42, 0xC0C0C0);
        graphics.drawString(this.font, "Lifecycle: " + selected.lifecycleStatus(), left, top + 56, 0xC0C0C0);
        graphics.drawString(this.font, "Conversation: " + selected.conversationStatus(), left, top + 70, 0xC0C0C0);
    }

    private void sendSpawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendSpawnRequest();
        } else {
            OpenPlayerRequestSender.sendSpawnRequest(selected.id());
        }
    }

    private void sendDespawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendDespawnRequest();
        } else {
            OpenPlayerRequestSender.sendDespawnRequest(selected.id());
        }
    }

    private void sendFollow() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendFollowOwnerRequest();
        } else {
            OpenPlayerRequestSender.sendFollowOwnerRequest(selected.id());
        }
    }

    private void sendStop() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendStopRequest();
        } else {
            OpenPlayerRequestSender.sendStopRequest(selected.id());
        }
    }

    private void sendCommandText() {
        String value = commandInput.getValue().trim();
        if (!value.isEmpty()) {
            LocalCharacterListEntry selected = selectedCharacter();
            if (selected == null) {
                OpenPlayerRequestSender.sendCommandTextRequest(value);
            } else {
                OpenPlayerRequestSender.sendCommandTextRequest(selected.id(), value);
            }
            commandInput.setValue("");
        }
    }

    private LocalCharacterListEntry selectedCharacter() {
        if (selectedCharacterId == null) {
            return null;
        }
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            if (character.id().equals(selectedCharacterId)) {
                return character;
            }
        }
        selectedCharacterId = null;
        return null;
    }

    private String characterKey() {
        List<String> parts = new ArrayList<>();
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            parts.add(character.id() + ":" + character.lifecycleStatus());
        }
        parts.addAll(OpenPlayerClientStatus.characterErrors());
        return String.join("|", parts);
    }

    private String fit(String value, int width) {
        if (this.font.width(value) <= width) {
            return value;
        }
        String suffix = "...";
        String shortened = value;
        while (!shortened.isEmpty() && this.font.width(shortened + suffix) > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + suffix;
    }
}
