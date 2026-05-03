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
    private static final Component PROVIDER_ENDPOINT_INPUT = Component.literal("Provider endpoint URL");
    private static final Component PROVIDER_MODEL_INPUT = Component.literal("Provider model");
    private static final Component PROVIDER_API_KEY_INPUT = Component.literal("API key; blank preserves existing key");
    private static final int BUTTON_WIDTH = 142;
    private static final int COMMAND_INPUT_WIDTH = 220;
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final int TAB_TOP = 42;
    private EditBox commandInput;
    private EditBox providerEndpointInput;
    private EditBox providerModelInput;
    private EditBox providerApiKeyInput;
    private String commandDraft = "";
    private String providerEndpointDraft = "";
    private String providerModelDraft = "";
    private String providerApiKeyDraft = "";
    private boolean clearApiKeyOnSave;
    private String selectedAssignmentId;
    private String renderedCharacterKey = "";
    private int pageIndex;
    private ControlPage controlPage = ControlPage.MAIN;

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
        commandDraft = commandInput == null ? commandDraft : commandInput.getValue();
        providerEndpointDraft = providerEndpointInput == null ? providerEndpointDraft : providerEndpointInput.getValue();
        providerModelDraft = providerModelInput == null ? providerModelDraft : providerModelInput.getValue();
        providerApiKeyDraft = providerApiKeyInput == null ? providerApiKeyDraft : providerApiKeyInput.getValue();
        rebuildControlWidgets(keepSelectedVisible);
        if (commandInput != null) {
            commandInput.setValue(commandDraft);
        }
        if (providerEndpointInput != null) {
            providerEndpointInput.setValue(providerEndpointDraft);
        }
        if (providerModelInput != null) {
            providerModelInput.setValue(providerModelDraft);
        }
        if (providerApiKeyInput != null) {
            providerApiKeyInput.setValue(providerApiKeyDraft);
        }
    }

    private void rebuildControlWidgets(boolean keepSelectedVisible) {
        this.clearWidgets();
        commandInput = null;
        providerEndpointInput = null;
        providerModelInput = null;
        providerApiKeyInput = null;
        renderedCharacterKey = characterKey();
        OpenPlayerControlLayout.Columns columns = OpenPlayerControlLayout.columns(this.width);
        int margin = OpenPlayerControlLayout.MARGIN;
        int listWidth = columns.listWidth();
        int rightLeft = columns.rightLeft();
        int rightWidth = columns.rightWidth();

        List<LocalCharacterListEntry> characters = OpenPlayerClientStatus.characters();
        int visibleAssignments = visibleAssignmentCount();
        pageIndex = pageIndexForRebuild(characters, visibleAssignments, keepSelectedVisible);
        OpenPlayerGalleryPage page = OpenPlayerGalleryPage.of(characters.size(), visibleAssignments, pageIndex);
        pageIndex = page.pageIndex();
        for (int index = page.firstIndex(); index < page.lastExclusiveIndex(); index++) {
            LocalCharacterListEntry character = characters.get(index);
            int row = index - page.firstIndex();
            int y = 58 + row * (OpenPlayerControlLayout.BUTTON_HEIGHT + 4);
            int characterIndex = index;
            this.addRenderableWidget(Button.builder(Component.literal(fit(galleryButtonLabel(character), listWidth - 12)), button -> {
                        selectedAssignmentId = character.assignmentId();
                        pageIndex = OpenPlayerGalleryPage.pageForItemIndex(characterIndex, visibleAssignments);
                        rebuildControlWidgetsPreservingCommandText(true);
                    })
                    .bounds(margin, y, listWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build());
        }
        int pagerTop = 58 + visibleAssignments * (OpenPlayerControlLayout.BUTTON_HEIGHT + 4) + 2;
        if (page.pageCount() > 1) {
            int pagerButtonWidth = Math.max(45, (listWidth - 62) / 2);
            Button previous = Button.builder(Component.literal("Prev"), button -> {
                        pageIndex = Math.max(0, pageIndex - 1);
                        rebuildControlWidgetsPreservingCommandText(false);
                    })
                    .bounds(margin, pagerTop, pagerButtonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            previous.active = page.hasPrevious();
            this.addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("Next"), button -> {
                        pageIndex = pageIndex + 1;
                        rebuildControlWidgetsPreservingCommandText(false);
                    })
                    .bounds(margin + listWidth - pagerButtonWidth, pagerTop, pagerButtonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            next.active = page.hasNext();
            this.addRenderableWidget(next);
        }

        addPageTabs(rightLeft, rightWidth);
        if (controlPage == ControlPage.PROVIDER) {
            addProviderWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.MAIN) {
            addMainWidgets(rightLeft, rightWidth);
        }
    }

    private void addPageTabs(int rightLeft, int rightWidth) {
        int tabSpacing = 4;
        int tabWidth = Math.max(52, Math.min(82, (rightWidth - tabSpacing * 2) / 3));
        int tabsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, tabWidth * 3 + tabSpacing * 2);
        ControlPage[] pages = ControlPage.values();
        for (int index = 0; index < pages.length; index++) {
            ControlPage page = pages[index];
            Button tab = Button.builder(Component.literal(page.label()), button -> {
                        controlPage = page;
                        rebuildControlWidgetsPreservingCommandText(true);
                    })
                    .bounds(tabsLeft + index * (tabWidth + tabSpacing), TAB_TOP, tabWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            tab.active = controlPage != page;
            this.addRenderableWidget(tab);
        }
    }

    private void addMainWidgets(int rightLeft, int rightWidth) {
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(58, (rightWidth - OpenPlayerControlLayout.BUTTON_SPACING) / 2));
        int buttonsWidth = buttonWidth * 2 + OpenPlayerControlLayout.BUTTON_SPACING;
        int buttonsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonsWidth);
        int leftColumn = buttonsLeft;
        int rightColumn = buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING;
        int rowTop = OpenPlayerControlLayout.mainActionRowTop();
        int rowStep = OpenPlayerControlLayout.BUTTON_HEIGHT + OpenPlayerControlLayout.BUTTON_SPACING;

        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Spawn Default NPC" : "Spawn Selected"), button -> sendSpawn())
                .bounds(leftColumn, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Despawn Default" : "Despawn Selected"), button -> sendDespawn())
                .bounds(rightColumn, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Default Follow" : "Selected Follow"), button -> sendFollow())
                .bounds(leftColumn, rowTop + rowStep, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Stop Default" : "Stop Selected"), button -> sendStop())
                .bounds(rightColumn, rowTop + rowStep, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Reload Local List"), button -> OpenPlayerRequestSender.sendCharacterListRequest())
                .bounds(leftColumn, rowTop + rowStep * 2, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        Button exportButton = Button.builder(Component.literal("Export Selected"), button -> sendExportSelected())
                .bounds(rightColumn, rowTop + rowStep * 2, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        exportButton.active = selectedCharacter() != null;
        this.addRenderableWidget(exportButton);
        int inputWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, COMMAND_INPUT_WIDTH);
        commandInput = new EditBox(
                this.font,
                OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, inputWidth),
                rowTop + rowStep * 3,
                inputWidth,
                OpenPlayerControlLayout.BUTTON_HEIGHT,
                COMMAND_INPUT
        );
        commandInput.setMaxLength(MAX_COMMAND_TEXT_LENGTH);
        commandInput.setHint(COMMAND_INPUT);
        this.addRenderableWidget(commandInput);
        this.addRenderableWidget(Button.builder(Component.literal(selectedCharacter() == null ? "Send to Default" : "Send to Selected"), button -> sendCommandText())
                .bounds(leftColumn, rowTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Import File Name"), button -> sendImportFileName())
                .bounds(rightColumn, rowTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    private void addProviderWidgets(int rightLeft, int rightWidth) {
        int inputWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, COMMAND_INPUT_WIDTH);
        int inputLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, inputWidth);
        int buttonWidth = Math.min(BUTTON_WIDTH, rightWidth);
        int controlsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonWidth);
        int providerTop = OpenPlayerControlLayout.PAGE_TOP + 16;
        int rowStep = OpenPlayerControlLayout.BUTTON_HEIGHT + OpenPlayerControlLayout.BUTTON_SPACING;
        providerEndpointInput = new EditBox(this.font, inputLeft, providerTop, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_ENDPOINT_INPUT);
        providerEndpointInput.setMaxLength(512);
        providerEndpointInput.setHint(PROVIDER_ENDPOINT_INPUT);
        this.addRenderableWidget(providerEndpointInput);
        providerModelInput = new EditBox(this.font, inputLeft, providerTop + rowStep, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_MODEL_INPUT);
        providerModelInput.setMaxLength(128);
        providerModelInput.setHint(PROVIDER_MODEL_INPUT);
        this.addRenderableWidget(providerModelInput);
        providerApiKeyInput = new EditBox(this.font, inputLeft, providerTop + rowStep * 2, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_API_KEY_INPUT);
        providerApiKeyInput.setMaxLength(512);
        providerApiKeyInput.setHint(PROVIDER_API_KEY_INPUT);
        this.addRenderableWidget(providerApiKeyInput);
        this.addRenderableWidget(Button.builder(Component.literal(clearApiKeyOnSave ? "Clear Key On Save" : "Preserve Blank Key"), button -> {
                    clearApiKeyOnSave = !clearApiKeyOnSave;
                    rebuildControlWidgetsPreservingCommandText(true);
                })
                .bounds(controlsLeft, providerTop + rowStep * 3, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Save Provider Config"), button -> sendProviderConfig())
                .bounds(controlsLeft, providerTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        OpenPlayerControlLayout.Columns columns = OpenPlayerControlLayout.columns(this.width);
        int margin = OpenPlayerControlLayout.MARGIN;
        int listWidth = columns.listWidth();
        int rightLeft = columns.rightLeft();
        int rightWidth = columns.rightWidth();
        OpenPlayerGalleryPage page = OpenPlayerGalleryPage.of(OpenPlayerClientStatus.characters().size(), visibleAssignmentCount(), pageIndex);
        graphics.drawString(this.font, "Local assignments: " + OpenPlayerClientStatus.characterListStatus(), margin, 42, 0xFFFFFF);
        graphics.drawString(this.font, page.label() + "  Total " + page.totalItems(), margin, 58 + visibleAssignmentCount() * 24 + 6, 0xA0A0A0);
        renderCharacterMessages(graphics, margin, 58 + visibleAssignmentCount() * 24 + 32, listWidth);
        if (controlPage == ControlPage.STATUS) {
            renderStatusPage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else if (controlPage == ControlPage.PROVIDER) {
            renderProviderPage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else {
            renderSelectedDetails(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderProviderPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, "Provider Config", left, top, width, 0xFFFFFF);
        drawFitted(graphics, "Blank API key preserves existing key unless clear is selected.", left, top + 150, width, 0xA0A0A0);
        drawFitted(graphics, "API key values are never displayed by this screen.", left, top + 164, width, 0xA0A0A0);
    }

    private void renderStatusPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, "Automation: " + OpenPlayerClientStatus.automationStatus(), left, top, width, 0xC0C0C0);
        drawFitted(graphics, "Parser: " + OpenPlayerClientStatus.parserStatus(), left, top + 14, width, 0xC0C0C0);
        drawFitted(graphics, "Endpoint: " + OpenPlayerClientStatus.endpointStatus(), left, top + 28, width, 0xC0C0C0);
        drawFitted(graphics, "Model: " + OpenPlayerClientStatus.modelStatus(), left, top + 42, width, 0xC0C0C0);
        drawFitted(graphics, "API key: " + OpenPlayerClientStatus.apiKeyStatus(), left, top + 56, width, 0xC0C0C0);
        drawFitted(graphics, "Character files: " + OpenPlayerClientStatus.characterFileOperationStatus(), left, top + 70, width, 0xC0C0C0);
        drawFitted(graphics, "Provider config: blank API key preserves existing key unless clear is selected.", left, top + 92, width, 0xA0A0A0);
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            drawFitted(graphics, "Selected: default spawn path", left, top + 114, width, 0xFFFFFF);
            return;
        }
        drawFitted(graphics, "Selected: " + selected.displayName(), left, top + 114, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, "Description: " + (selected.description().isEmpty() ? "none" : selected.description()), left, top + 128, width, 0xC0C0C0);
        drawFitted(graphics, "Skin: " + selected.skinStatus(), left, top + 142, width, 0xC0C0C0);
        int eventTop = top + 156;
        if (selected.conversationEvents().isEmpty()) {
            drawFitted(graphics, "Spoken status: no local lines yet.", left, eventTop, width, 0xA0A0A0);
            return;
        }
        drawFitted(graphics, "Spoken status: " + selected.conversationEvents().get(selected.conversationEvents().size() - 1), left, eventTop, width, 0xD0D0D0);
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
            drawFitted(graphics, "Selected: default spawn path", left, top, width, 0xFFFFFF);
            drawFitted(graphics, "Choose a character to target actions.", left, top + 14, width, 0xA0A0A0);
            drawFitted(graphics, "Actions: Spawn uses the original default NPC.", left, top + 28, width, 0xC0C0C0);
            drawFitted(graphics, "Use Provider and Status pages for setup details.", left, top + 42, width, 0xA0A0A0);
            return;
        }
        drawFitted(graphics, "Selected: " + selected.displayName(), left, top, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, "Assignment: " + selected.assignmentId(), left, top + 14, width, 0xC0C0C0);
        drawFitted(graphics, "Character: " + selected.characterId(), left, top + 28, width, 0xC0C0C0);
        drawFitted(graphics, "Lifecycle: " + selected.lifecycleStatus(), left, top + 42, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, "Conversation: " + selected.conversationStatus(), left, top + 56, width, 0xC0C0C0);
        drawFitted(graphics, "Details and recent spoken status are on Status.", left, top + 70, width, 0xA0A0A0);
    }

    private void drawFitted(GuiGraphics graphics, String value, int left, int top, int width, int color) {
        graphics.drawString(this.font, fit(value, width), left, top, color);
    }

    private void sendExportSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendCharacterExportRequest(selected.characterId());
        }
    }

    private void sendImportFileName() {
        String value = commandInput.getValue().trim();
        if (!value.isEmpty()) {
            OpenPlayerRequestSender.sendCharacterImportRequest(value);
            commandInput.setValue("");
            commandDraft = "";
        }
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
            commandDraft = "";
        }
    }

    private void sendProviderConfig() {
        providerEndpointDraft = providerEndpointInput.getValue();
        providerModelDraft = providerModelInput.getValue();
        providerApiKeyDraft = providerApiKeyInput.getValue();
        OpenPlayerRequestSender.sendProviderConfigSaveRequest(
                providerEndpointDraft,
                providerModelDraft,
                providerApiKeyDraft,
                clearApiKeyOnSave
        );
        providerApiKeyInput.setValue("");
        providerApiKeyDraft = "";
        clearApiKeyOnSave = false;
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
        parts.add(OpenPlayerClientStatus.parserStatus());
        parts.add(OpenPlayerClientStatus.endpointStatus());
        parts.add(OpenPlayerClientStatus.modelStatus());
        parts.add(OpenPlayerClientStatus.apiKeyStatus());
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            parts.add(character.assignmentId() + ":" + character.lifecycleStatus() + ":" + character.conversationStatus());
            parts.addAll(character.conversationEvents());
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
        return OpenPlayerControlLayout.visibleAssignmentCount(this.height);
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
        if (width <= 0) {
            return "";
        }
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

    private enum ControlPage {
        MAIN("Main"),
        PROVIDER("Provider"),
        STATUS("Status");

        private final String label;

        ControlPage(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }
}
