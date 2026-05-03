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
    private static final int BUTTON_WIDTH = 142;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 6;
    private static final int COMMAND_INPUT_WIDTH = 220;
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final int MIN_VISIBLE_ASSIGNMENTS = 3;
    private static final int MAX_VISIBLE_ASSIGNMENTS = 6;
    private EditBox commandInput;
    private String selectedAssignmentId;
    private String renderedCharacterKey = "";
    private int pageIndex;

    public OpenPlayerControlScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        rebuildControlWidgets(true);
        OpenPlayerRequestSender.sendStatusRequest();
        OpenPlayerRequestSender.sendCharacterListRequest();
    }

    @Override
    public void tick() {
        String key = characterKey();
        if (!key.equals(renderedCharacterKey)) {
            rebuildControlWidgetsPreservingCommandText(true);
        }
    }

    private void rebuildControlWidgetsPreservingCommandText(boolean keepSelectedVisible) {
        String commandText = commandInput == null ? "" : commandInput.getValue();
        rebuildControlWidgets(keepSelectedVisible);
        commandInput.setValue(commandText);
    }

    private void rebuildControlWidgets(boolean keepSelectedVisible) {
        this.clearWidgets();
        renderedCharacterKey = characterKey();
        int margin = 12;
        int listWidth = Math.min(210, Math.max(124, this.width / 3));
        int rightLeft = margin + listWidth + 12;
        int rightWidth = Math.max(120, this.width - rightLeft - margin);
        int controlWidth = Math.min(BUTTON_WIDTH, rightWidth);
        int controlsLeft = rightLeft + Math.max(0, (rightWidth - controlWidth) / 2);
        int top = Math.max(90, Math.min(this.height - 84, 124));

        List<LocalCharacterListEntry> characters = OpenPlayerClientStatus.characters();
        int visibleAssignments = visibleAssignmentCount();
        pageIndex = pageIndexForRebuild(characters, visibleAssignments, keepSelectedVisible);
        OpenPlayerGalleryPage page = OpenPlayerGalleryPage.of(characters.size(), visibleAssignments, pageIndex);
        pageIndex = page.pageIndex();
        for (int index = page.firstIndex(); index < page.lastExclusiveIndex(); index++) {
            LocalCharacterListEntry character = characters.get(index);
            int row = index - page.firstIndex();
            int y = 58 + row * (BUTTON_HEIGHT + 4);
            int characterIndex = index;
            this.addRenderableWidget(Button.builder(Component.literal(fit(galleryButtonLabel(character), listWidth - 12)), button -> {
                        selectedAssignmentId = character.assignmentId();
                        pageIndex = OpenPlayerGalleryPage.pageForItemIndex(characterIndex, visibleAssignments);
                        rebuildControlWidgetsPreservingCommandText(true);
                    })
                    .bounds(margin, y, listWidth, BUTTON_HEIGHT)
                    .build());
        }
        int pagerTop = 58 + visibleAssignments * (BUTTON_HEIGHT + 4) + 2;
        if (page.pageCount() > 1) {
            int pagerButtonWidth = Math.max(45, (listWidth - 62) / 2);
            Button previous = Button.builder(Component.literal("Prev"), button -> {
                        pageIndex = Math.max(0, pageIndex - 1);
                        rebuildControlWidgetsPreservingCommandText(false);
                    })
                    .bounds(margin, pagerTop, pagerButtonWidth, BUTTON_HEIGHT)
                    .build();
            previous.active = page.hasPrevious();
            this.addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("Next"), button -> {
                        pageIndex = pageIndex + 1;
                        rebuildControlWidgetsPreservingCommandText(false);
                    })
                    .bounds(margin + listWidth - pagerButtonWidth, pagerTop, pagerButtonWidth, BUTTON_HEIGHT)
                    .build();
            next.active = page.hasNext();
            this.addRenderableWidget(next);
        }

        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Spawn Default NPC" : "Spawn Selected"), button -> sendSpawn())
                .bounds(controlsLeft, top, controlWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Despawn Default" : "Despawn Selected"), button -> sendDespawn())
                .bounds(controlsLeft, top + BUTTON_HEIGHT + BUTTON_SPACING, controlWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Default Follow" : "Selected Follow"), button -> sendFollow())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 2, controlWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Stop Default" : "Stop Selected"), button -> sendStop())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 3, controlWidth, BUTTON_HEIGHT)
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
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Send to Default" : "Send to Selected"), button -> sendCommandText())
                .bounds(controlsLeft, top + (BUTTON_HEIGHT + BUTTON_SPACING) * 5, controlWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int margin = 12;
        int listWidth = Math.min(210, Math.max(124, this.width / 3));
        int rightLeft = margin + listWidth + 12;
        int rightWidth = Math.max(120, this.width - rightLeft - margin);
        OpenPlayerGalleryPage page = OpenPlayerGalleryPage.of(OpenPlayerClientStatus.characters().size(), visibleAssignmentCount(), pageIndex);
        graphics.drawString(this.font, "Local assignments: " + OpenPlayerClientStatus.characterListStatus(), margin, 42, 0xFFFFFF);
        graphics.drawString(this.font, page.label() + "  Total " + page.totalItems(), margin, 58 + visibleAssignmentCount() * 24 + 6, 0xA0A0A0);
        renderCharacterMessages(graphics, margin, 58 + visibleAssignmentCount() * 24 + 32, listWidth);
        renderSelectedDetails(graphics, rightLeft, 42, rightWidth);
        int statusLeft = rightLeft;
        int statusTop = Math.max(42, this.height - 66);
        graphics.drawString(this.font, "Automation: " + OpenPlayerClientStatus.automationStatus(), statusLeft, statusTop, 0xC0C0C0);
        graphics.drawString(this.font, "Parser: " + OpenPlayerClientStatus.parserStatus(), statusLeft, statusTop + 12, 0xC0C0C0);
        graphics.drawString(this.font, "Endpoint: " + OpenPlayerClientStatus.endpointStatus(), statusLeft, statusTop + 24, 0xC0C0C0);
        graphics.drawString(this.font, "Model: " + OpenPlayerClientStatus.modelStatus(), statusLeft, statusTop + 36, 0xC0C0C0);
        graphics.drawString(this.font, "API key: " + OpenPlayerClientStatus.apiKeyStatus(), statusLeft, statusTop + 48, 0xC0C0C0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCharacterMessages(GuiGraphics graphics, int left, int top, int width) {
        if ("loading".equals(OpenPlayerClientStatus.characterListStatus())) {
            graphics.drawString(this.font, "Loading local assignments...", left, top, 0xA0A0A0);
            return;
        }
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
            graphics.drawString(this.font, "Actions: Spawn uses the original default NPC.", left, top + 28, 0xC0C0C0);
            return;
        }
        graphics.drawString(this.font, "Selected: " + fit(selected.displayName(), width - 58), left, top, lifecycleColor(selected.lifecycleStatus()));
        graphics.drawString(this.font, "Assignment: " + selected.assignmentId(), left, top + 14, 0xC0C0C0);
        graphics.drawString(this.font, "Character: " + selected.characterId(), left, top + 28, 0xC0C0C0);
        graphics.drawString(this.font, "Description: " + fit(selected.description().isEmpty() ? "none" : selected.description(), width - 70), left, top + 42, 0xC0C0C0);
        graphics.drawString(this.font, "Skin: " + selected.skinStatus(), left, top + 56, 0xC0C0C0);
        graphics.drawString(this.font, "Lifecycle: " + selected.lifecycleStatus(), left, top + 70, lifecycleColor(selected.lifecycleStatus()));
        graphics.drawString(this.font, "Conversation: " + selected.conversationStatus(), left, top + 84, 0xC0C0C0);
        graphics.drawString(this.font, "Actions: spawn, despawn, follow, stop, command.", left, top + 98, 0xA0A0A0);
    }

    private void sendSpawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendSpawnRequest();
        } else {
            OpenPlayerRequestSender.sendSpawnRequest(selected.assignmentId());
        }
    }

    private void sendDespawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendDespawnRequest();
        } else {
            OpenPlayerRequestSender.sendDespawnRequest(selected.assignmentId());
        }
    }

    private void sendFollow() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendFollowOwnerRequest();
        } else {
            OpenPlayerRequestSender.sendFollowOwnerRequest(selected.assignmentId());
        }
    }

    private void sendStop() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            OpenPlayerRequestSender.sendStopRequest();
        } else {
            OpenPlayerRequestSender.sendStopRequest(selected.assignmentId());
        }
    }

    private void sendCommandText() {
        String value = commandInput.getValue().trim();
        if (!value.isEmpty()) {
            LocalCharacterListEntry selected = selectedCharacter();
            if (selected == null) {
                OpenPlayerRequestSender.sendCommandTextRequest(value);
            } else {
                OpenPlayerRequestSender.sendCommandTextRequest(selected.assignmentId(), value);
            }
            commandInput.setValue("");
        }
    }

    private LocalCharacterListEntry selectedCharacter() {
        if (selectedAssignmentId == null) {
            return null;
        }
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            if (character.assignmentId().equals(selectedAssignmentId)) {
                return character;
            }
        }
        selectedAssignmentId = null;
        return null;
    }

    private String characterKey() {
        List<String> parts = new ArrayList<>();
        parts.add(OpenPlayerClientStatus.characterListStatus());
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            parts.add(character.assignmentId() + ":" + character.lifecycleStatus() + ":" + character.conversationStatus());
        }
        parts.addAll(OpenPlayerClientStatus.characterErrors());
        return String.join("|", parts);
    }

    private int pageIndexForRebuild(List<LocalCharacterListEntry> characters, int visibleAssignments, boolean keepSelectedVisible) {
        if (selectedAssignmentId == null) {
            return OpenPlayerGalleryPage.of(characters.size(), visibleAssignments, pageIndex).pageIndex();
        }
        for (int index = 0; index < characters.size(); index++) {
            if (characters.get(index).assignmentId().equals(selectedAssignmentId)) {
                if (keepSelectedVisible) {
                    return OpenPlayerGalleryPage.pageForItemIndex(index, visibleAssignments);
                }
                return OpenPlayerGalleryPage.of(characters.size(), visibleAssignments, pageIndex).pageIndex();
            }
        }
        selectedAssignmentId = null;
        return OpenPlayerGalleryPage.of(characters.size(), visibleAssignments, pageIndex).pageIndex();
    }

    private int visibleAssignmentCount() {
        int availableHeight = this.height - 150;
        int byHeight = availableHeight <= 0 ? MIN_VISIBLE_ASSIGNMENTS : availableHeight / (BUTTON_HEIGHT + 4);
        return Math.max(MIN_VISIBLE_ASSIGNMENTS, Math.min(MAX_VISIBLE_ASSIGNMENTS, byHeight));
    }

    private String galleryButtonLabel(LocalCharacterListEntry character) {
        String selectedPrefix = character.assignmentId().equals(selectedAssignmentId) ? "> " : "";
        return selectedPrefix + character.displayName() + " [" + lifecycleLabel(character.lifecycleStatus()) + "]";
    }

    private String lifecycleLabel(String lifecycleStatus) {
        String normalized = lifecycleStatus.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spawn") || normalized.contains("active")) {
            return "active";
        }
        if (normalized.contains("despawn")) {
            return "despawned";
        }
        return lifecycleStatus;
    }

    private int lifecycleColor(String lifecycleStatus) {
        String normalized = lifecycleStatus.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spawn") || normalized.contains("active")) {
            return 0x80FF80;
        }
        if (normalized.contains("despawn")) {
            return 0xC0C0C0;
        }
        return 0xFFD080;
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
