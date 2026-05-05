package dev.soffits.openplayer.client;

import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public final class OpenPlayerControlScreen extends OpenPlayerControlScreenBase {
    public OpenPlayerControlScreen() {
        super();
    }

    protected void init() {
        rebuildControlWidgets(true);
        requestStatusForSelection();
        OpenPlayerRequestSender.sendCharacterListRequest();
    }

    @Override
    public void tick() {
        String key = characterKey();
        if (!key.equals(renderedCharacterKey)) {
            rebuildControlWidgetsPreservingDrafts(true);
        }
    }

    protected void rebuildControlWidgetsPreservingDrafts(boolean keepSelectedVisible) {
        providerEndpointDraft = providerEndpointInput == null ? providerEndpointDraft : providerEndpointInput.getValue();
        providerModelDraft = providerModelInput == null ? providerModelDraft : providerModelInput.getValue();
        providerApiKeyDraft = providerApiKeyInput == null ? providerApiKeyDraft : providerApiKeyInput.getValue();
        rebuildControlWidgets(keepSelectedVisible);
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

    protected void rebuildControlWidgets(boolean keepSelectedVisible) {
        this.clearWidgets();
        providerEndpointInput = null;
        providerModelInput = null;
        providerApiKeyInput = null;
        profileIdInput = null;
        profileDisplayNameInput = null;
        profileDescriptionInput = null;
        profileSkinFileInput = null;
        profileRoleInput = null;
        profilePromptInput = null;
        profileSettingsInput = null;
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
                        requestStatusForSelection();
                        rebuildControlWidgetsPreservingDrafts(true);
                    })
                    .bounds(margin, y, listWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build());
        }
        int pagerTop = 58 + visibleAssignments * (OpenPlayerControlLayout.BUTTON_HEIGHT + 4) + 2;
        if (page.pageCount() > 1) {
            int pagerButtonWidth = Math.max(45, (listWidth - 62) / 2);
            Button previous = Button.builder(Component.translatable("screen.openplayer.controls.previous_page"), button -> {
                        pageIndex = Math.max(0, pageIndex - 1);
                        rebuildControlWidgetsPreservingDrafts(false);
                    })
                    .bounds(margin, pagerTop, pagerButtonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            previous.active = page.hasPrevious();
            this.addRenderableWidget(previous);
            Button next = Button.builder(Component.translatable("screen.openplayer.controls.next_page"), button -> {
                        pageIndex = pageIndex + 1;
                        rebuildControlWidgetsPreservingDrafts(false);
                    })
                    .bounds(margin + listWidth - pagerButtonWidth, pagerTop, pagerButtonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            next.active = page.hasNext();
            this.addRenderableWidget(next);
        }

        addPageTabs(rightLeft, rightWidth);
        if (controlPage == ControlPage.PROVIDER) {
            hydrateProviderDraftsFromStatus();
            addProviderWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.PROFILE) {
            addProfileWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.IMPORTS) {
            addImportWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.MAIN) {
            addMainWidgets(rightLeft, rightWidth);
        }
    }

    protected void addPageTabs(int rightLeft, int rightWidth) {
        int tabSpacing = 4;
        int tabWidth = Math.max(48, Math.min(72, (rightWidth - tabSpacing * (ControlPage.values().length - 1)) / ControlPage.values().length));
        int tabsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, tabWidth * ControlPage.values().length + tabSpacing * (ControlPage.values().length - 1));
        ControlPage[] pages = ControlPage.values();
        for (int index = 0; index < pages.length; index++) {
            ControlPage page = pages[index];
            Button tab = Button.builder(Component.translatable(page.translationKey()), button -> {
                        controlPage = page;
                        rebuildControlWidgetsPreservingDrafts(true);
                    })
                    .bounds(tabsLeft + index * (tabWidth + tabSpacing), TAB_TOP, tabWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            tab.active = controlPage != page;
            this.addRenderableWidget(tab);
        }
    }

    protected void addMainWidgets(int rightLeft, int rightWidth) {
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(58, (rightWidth - OpenPlayerControlLayout.BUTTON_SPACING) / 2));
        int buttonsWidth = buttonWidth * 2 + OpenPlayerControlLayout.BUTTON_SPACING;
        int buttonsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonsWidth);
        int leftColumn = buttonsLeft;
        int rightColumn = buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING;
        int rowTop = OpenPlayerControlLayout.mainActionRowTop();
        int rowStep = OpenPlayerControlLayout.BUTTON_HEIGHT + OpenPlayerControlLayout.BUTTON_SPACING;

        boolean hasSelection = selectedCharacter() != null;
        Button spawnButton = Button.builder(Component.translatable("screen.openplayer.controls.spawn_selected"), button -> sendSpawn())
                .bounds(leftColumn, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        spawnButton.active = hasSelection;
        this.addRenderableWidget(spawnButton);
        Button despawnButton = Button.builder(Component.translatable("screen.openplayer.controls.despawn_selected"), button -> sendDespawn())
                .bounds(rightColumn, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        despawnButton.active = hasSelection;
        this.addRenderableWidget(despawnButton);
        Button followButton = Button.builder(Component.translatable(followingSelected() ? "screen.openplayer.controls.stop_following" : "screen.openplayer.controls.start_following"), button -> sendFollowToggle())
                .bounds(leftColumn, rowTop + rowStep, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        followButton.active = hasSelection;
        this.addRenderableWidget(followButton);
        Button stopButton = Button.builder(Component.translatable("screen.openplayer.controls.stop_selected"), button -> sendStop())
                .bounds(rightColumn, rowTop + rowStep, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        stopButton.active = hasSelection;
        this.addRenderableWidget(stopButton);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.reload_local_list"), button -> OpenPlayerRequestSender.sendCharacterListRequest())
                .bounds(leftColumn, rowTop + rowStep * 2, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        Button exportButton = Button.builder(Component.translatable("screen.openplayer.controls.export_selected"), button -> sendExportSelected())
                .bounds(rightColumn, rowTop + rowStep * 2, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        exportButton.active = selectedCharacter() != null;
        this.addRenderableWidget(exportButton);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.profile_page"), button -> {
                    controlPage = ControlPage.PROFILE;
                    rebuildControlWidgetsPreservingDrafts(true);
                })
                .bounds(OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonWidth), rowTop + rowStep * 3, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    protected void addProviderWidgets(int rightLeft, int rightWidth) {
        int inputWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, CONTROL_INPUT_WIDTH);
        int inputLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, inputWidth);
        int buttonWidth = Math.min(BUTTON_WIDTH, rightWidth);
        int controlsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonWidth);
        int providerTop = OpenPlayerControlLayout.PROVIDER_ROW_TOP;
        int rowStep = OpenPlayerControlLayout.PROVIDER_ROW_STEP;
        providerEndpointInput = new EditBox(this.font, inputLeft, providerTop, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_ENDPOINT_INPUT);
        providerEndpointInput.setMaxLength(512);
        providerEndpointInput.setHint(PROVIDER_ENDPOINT_INPUT);
        providerEndpointInput.setValue(providerEndpointDraft);
        this.addRenderableWidget(providerEndpointInput);
        providerModelInput = new EditBox(this.font, inputLeft, providerTop + rowStep, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_MODEL_INPUT);
        providerModelInput.setMaxLength(128);
        providerModelInput.setHint(PROVIDER_MODEL_INPUT);
        providerModelInput.setValue(providerModelDraft);
        this.addRenderableWidget(providerModelInput);
        providerApiKeyInput = new EditBox(this.font, inputLeft, providerTop + rowStep * 2, inputWidth, OpenPlayerControlLayout.BUTTON_HEIGHT, PROVIDER_API_KEY_INPUT);
        providerApiKeyInput.setMaxLength(512);
        providerApiKeyInput.setHint(PROVIDER_API_KEY_INPUT);
        this.addRenderableWidget(providerApiKeyInput);
        this.addRenderableWidget(Button.builder(Component.translatable(clearApiKeyOnSave ? "screen.openplayer.controls.clear_key_on_save" : "screen.openplayer.controls.preserve_blank_key"), button -> {
                    clearApiKeyOnSave = !clearApiKeyOnSave;
                    rebuildControlWidgetsPreservingDrafts(true);
                })
                .bounds(controlsLeft, providerTop + rowStep * 3, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.save_provider_config"), button -> sendProviderConfig())
                .bounds(controlsLeft, providerTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.test_provider"), button -> sendProviderTest())
                .bounds(controlsLeft, providerTop + rowStep * OpenPlayerControlLayout.PROVIDER_TEST_BUTTON_ROW, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    protected void addProfileWidgets(int rightLeft, int rightWidth) {
        int inputWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, CONTROL_INPUT_WIDTH);
        int inputLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, inputWidth);
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(58, (rightWidth - OpenPlayerControlLayout.BUTTON_SPACING) / 2));
        int buttonsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonWidth * 2 + OpenPlayerControlLayout.BUTTON_SPACING);
        int rowTop = OpenPlayerControlLayout.PROFILE_ROW_TOP;
        int rowStep = OpenPlayerControlLayout.PROFILE_ROW_STEP;
        LocalCharacterListEntry selected = selectedCharacter();
        profileIdInput = profileInput(inputLeft, rowTop, inputWidth, 64, PROFILE_ID_INPUT, selected == null ? newProfileId() : selected.characterId());
        profileDisplayNameInput = profileInput(inputLeft, rowTop + rowStep, inputWidth, 32, PROFILE_DISPLAY_NAME_INPUT, selected == null ? Component.translatable("screen.openplayer.controls.new_profile_display_name").getString() : selected.displayName());
        profileDescriptionInput = profileInput(inputLeft, rowTop + rowStep * 2, inputWidth, 1024, PROFILE_DESCRIPTION_INPUT, selected == null ? "" : selected.description());
        profileSkinFileInput = profileInput(inputLeft, rowTop + rowStep * 3, inputWidth, 256, PROFILE_SKIN_FILE_INPUT, selected == null ? "" : selected.localSkinFile());
        profileRoleInput = profileInput(inputLeft, rowTop + rowStep * 4, inputWidth, 64, PROFILE_ROLE_INPUT, selected == null ? "" : selected.defaultRoleId());
        profilePromptInput = profileInput(inputLeft, rowTop + rowStep * 5, inputWidth, 4096, PROFILE_PROMPT_INPUT, selected == null ? "" : selected.conversationPrompt());
        profileSettingsInput = profileInput(inputLeft, rowTop + rowStep * 6, inputWidth, 2048, PROFILE_SETTINGS_INPUT, selected == null ? "" : selected.conversationSettings());
        profileAllowWorldActions = selected != null && selected.allowWorldActions();
        this.addRenderableWidget(Button.builder(Component.translatable(profileAllowWorldActions ? "screen.openplayer.controls.world_actions_on" : "screen.openplayer.controls.world_actions_off"), button -> {
                    profileAllowWorldActions = !profileAllowWorldActions;
                    button.setMessage(Component.translatable(profileAllowWorldActions ? "screen.openplayer.controls.world_actions_on" : "screen.openplayer.controls.world_actions_off"));
                })
                .bounds(buttonsLeft, rowTop + rowStep * 7, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.save_profile"), button -> sendProfileSave())
                .bounds(buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING, rowTop + rowStep * 7, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.new_profile"), button -> {
                    selectedAssignmentId = null;
                    confirmDeleteSelected = false;
                    rebuildControlWidgetsPreservingDrafts(true);
                })
                .bounds(buttonsLeft, rowTop + rowStep * 8, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        Button duplicateButton = Button.builder(Component.translatable("screen.openplayer.controls.duplicate_profile"), button -> duplicateSelectedProfile())
                .bounds(buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING, rowTop + rowStep * 8, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        duplicateButton.active = selected != null;
        this.addRenderableWidget(duplicateButton);
        Button deleteButton = Button.builder(Component.translatable(confirmDeleteSelected ? "screen.openplayer.controls.confirm_delete_profile" : "screen.openplayer.controls.delete_profile"), button -> sendProfileDelete())
                .bounds(buttonsLeft, rowTop + rowStep * 9, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        deleteButton.active = selected != null && !protectedProfileSelected();
        this.addRenderableWidget(deleteButton);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.imports_page"), button -> {
                    controlPage = ControlPage.IMPORTS;
                    rebuildControlWidgetsPreservingDrafts(true);
                })
                .bounds(buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING, rowTop + rowStep * 9, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    protected EditBox profileInput(int left, int top, int width, int maxLength, Component hint, String value) {
        EditBox input = new EditBox(this.font, left, top, width, OpenPlayerControlLayout.BUTTON_HEIGHT, hint);
        input.setMaxLength(maxLength);
        input.setHint(hint);
        input.setValue(value == null ? "" : value);
        this.addRenderableWidget(input);
        return input;
    }

    protected void addImportWidgets(int rightLeft, int rightWidth) {
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(58, (rightWidth - OpenPlayerControlLayout.BUTTON_SPACING) / 2));
        int buttonsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, buttonWidth * 2 + OpenPlayerControlLayout.BUTTON_SPACING);
        int rowTop = OpenPlayerControlLayout.PAGE_TOP + 18;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.open_imports_folder"), button -> openImportsFolder())
                .bounds(buttonsLeft, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.refresh_imports"), button -> OpenPlayerRequestSender.sendCharacterListRequest())
                .bounds(buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING, rowTop, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        List<String> imports = OpenPlayerClientStatus.importFileNames();
        int y = rowTop + 34;
        int listWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, CONTROL_INPUT_WIDTH);
        int listLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, listWidth);
        for (int index = 0; index < Math.min(imports.size(), 6); index++) {
            int importIndex = index;
            Button fileButton = Button.builder(Component.literal(fit((index == selectedImportIndex ? "> " : "") + imports.get(index), listWidth - 10)), button -> {
                        selectedImportIndex = importIndex;
                        rebuildControlWidgetsPreservingDrafts(true);
                    })
                    .bounds(listLeft, y + index * 24, listWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            this.addRenderableWidget(fileButton);
        }
        Button importButton = Button.builder(Component.translatable("screen.openplayer.controls.import_selected"), button -> sendSelectedImport())
                .bounds(buttonsLeft, y + 6 * 24 + 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        importButton.active = !imports.isEmpty();
        this.addRenderableWidget(importButton);
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
        graphics.drawString(this.font, Component.translatable("screen.openplayer.controls.local_assignments", OpenPlayerClientStatus.characterListStatus()), margin, 42, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("screen.openplayer.controls.page_total", page.displayPageIndex(), page.pageCount(), page.totalItems()), margin, 58 + visibleAssignmentCount() * 24 + 6, 0xA0A0A0);
        renderCharacterMessages(graphics, margin, 58 + visibleAssignmentCount() * 24 + 32, listWidth);
        if (controlPage == ControlPage.STATUS) {
            renderStatusPage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else if (controlPage == ControlPage.PROVIDER) {
            renderProviderPage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else if (controlPage == ControlPage.PROFILE) {
            renderProfilePage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else if (controlPage == ControlPage.IMPORTS) {
            renderImportPage(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        } else {
            renderSelectedDetails(graphics, rightLeft, OpenPlayerControlLayout.PAGE_TOP, rightWidth);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    protected void renderProviderPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.provider_config"), left, top, width, 0xFFFFFF);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_test_status", OpenPlayerClientStatus.providerTestStatus()), left, OpenPlayerControlLayout.PROVIDER_STATUS_TOP, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_key_preserve_note"), left, OpenPlayerControlLayout.PROVIDER_NOTE_TOP, width, 0xA0A0A0);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_key_safe_note"), left, OpenPlayerControlLayout.PROVIDER_NOTE_TOP + 14, width, 0xA0A0A0);
    }

    protected void renderProfilePage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.profile_manager"), left, top, width, 0xFFFFFF);
        if (protectedProfileSelected()) {
            drawFitted(graphics, tr("screen.openplayer.controls.default_profile_delete_note"), left, OpenPlayerControlLayout.PROFILE_DEFAULT_NOTE_TOP, width, 0xA0A0A0);
        }
        drawFitted(graphics, tr("screen.openplayer.controls.profile_file_status", OpenPlayerClientStatus.characterFileOperationStatus()), left, OpenPlayerControlLayout.PROFILE_STATUS_TOP, width, 0xC0C0C0);
    }

    protected void renderImportPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.imports_title"), left, top, width, 0xFFFFFF);
        drawFitted(graphics, tr("screen.openplayer.controls.imports_hint"), left, top + 182, width, 0xA0A0A0);
        if (OpenPlayerClientStatus.importFileNames().isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_import_files"), left, top + 54, width, 0xA0A0A0);
        }
        drawFitted(graphics, tr("screen.openplayer.controls.profile_file_status", OpenPlayerClientStatus.characterFileOperationStatus()), left, top + 196, width, 0xC0C0C0);
    }

    protected void renderStatusPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.status_automation", OpenPlayerClientStatus.automationStatus()), left, top, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_parser", OpenPlayerClientStatus.parserStatus()), left, top + 14, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_endpoint", OpenPlayerClientStatus.endpointStatus()), left, top + 28, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_model", OpenPlayerClientStatus.modelStatus()), left, top + 42, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_api_key", OpenPlayerClientStatus.apiKeyStatus()), left, top + 56, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_test_status", OpenPlayerClientStatus.providerTestStatus()), left, top + 70, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_character_files", OpenPlayerClientStatus.characterFileOperationStatus()), left, top + 84, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.capability_status"), left, top + 106, width, 0xFFFFFF);
        List<String> capabilityLines = OpenPlayerClientStatus.capabilityStatusLines();
        int capabilityTop = top + 120;
        if (capabilityLines.isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_capability_status"), left, capabilityTop, width, 0xA0A0A0);
        } else {
            for (int index = 0; index < Math.min(capabilityLines.size(), 5); index++) {
                drawFitted(graphics, capabilityLines.get(index), left, capabilityTop + index * 10, width, 0xA0A0A0);
            }
        }
        drawFitted(graphics, tr("screen.openplayer.controls.debug_events"), left, top + 178, width, 0xFFFFFF);
        List<String> debugEvents = OpenPlayerClientStatus.debugEvents();
        int debugTop = top + 192;
        if (debugEvents.isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_debug_events"), left, debugTop, width, 0xA0A0A0);
        } else {
            int firstIndex = Math.max(0, debugEvents.size() - 3);
            for (int index = firstIndex; index < debugEvents.size(); index++) {
                drawFitted(graphics, debugEvents.get(index), left, debugTop + (index - firstIndex) * 12, width, 0xA0A0A0);
            }
        }
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_selected_assignment"), left, top + 234, width, 0xFFFFFF);
            return;
        }
        drawFitted(graphics, tr("screen.openplayer.controls.selected_value", selected.displayName()), left, top + 234, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, tr("screen.openplayer.controls.description_value", selected.description().isEmpty() ? tr("screen.openplayer.controls.none") : selected.description()), left, top + 248, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.skin_value", selected.skinStatus()), left, top + 262, width, 0xC0C0C0);
        int eventTop = top + 276;
        if (selected.conversationEvents().isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_spoken_status"), left, eventTop, width, 0xA0A0A0);
            return;
        }
        drawFitted(graphics, tr("screen.openplayer.controls.spoken_status", selected.conversationEvents().get(selected.conversationEvents().size() - 1)), left, eventTop, width, 0xD0D0D0);
    }

    protected void renderCharacterMessages(GuiGraphics graphics, int left, int top, int width) {
        if ("loading".equals(OpenPlayerClientStatus.characterListStatus())) {
            graphics.drawString(this.font, Component.translatable("screen.openplayer.controls.loading_assignments"), left, top, 0xA0A0A0);
            return;
        }
        if (OpenPlayerClientStatus.characters().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.openplayer.controls.no_local_characters"), left, top, 0xA0A0A0);
        }
        int y = top + 14;
        for (String error : OpenPlayerClientStatus.characterErrors()) {
            graphics.drawString(this.font, fit(error, width), left, y, 0xFF8888);
            y += 12;
        }
    }

    protected void renderSelectedDetails(GuiGraphics graphics, int left, int top, int width) {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_selected_assignment"), left, top, width, 0xFFFFFF);
            drawFitted(graphics, tr("screen.openplayer.controls.choose_assignment"), left, top + 14, width, 0xA0A0A0);
            drawFitted(graphics, tr("screen.openplayer.controls.no_default_action_note"), left, top + 28, width, 0xC0C0C0);
            drawFitted(graphics, tr("screen.openplayer.controls.setup_pages_note"), left, top + 42, width, 0xA0A0A0);
            return;
        }
        drawFitted(graphics, tr("screen.openplayer.controls.selected_value", selected.displayName()), left, top, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, tr("screen.openplayer.controls.assignment_value", selected.assignmentId()), left, top + 14, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.character_value", selected.characterId()), left, top + 28, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.lifecycle_value", selected.lifecycleStatus()), left, top + 42, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, tr("screen.openplayer.controls.conversation_value", selected.conversationStatus()), left, top + 56, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_page_note"), left, top + 70, width, 0xA0A0A0);
    }

    protected String tr(String key, Object... values) {
        return Component.translatable(key, values).getString();
    }

    protected void drawFitted(GuiGraphics graphics, String value, int left, int top, int width, int color) {
        graphics.drawString(this.font, fit(value, width), left, top, color);
    }
}
