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

public final class OpenPlayerControlScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.openplayer.controls.title");
    private static final Component COMMAND_INPUT = Component.translatable("screen.openplayer.controls.command_input");
    private static final Component PROVIDER_ENDPOINT_INPUT = Component.translatable("screen.openplayer.controls.provider_endpoint_input");
    private static final Component PROVIDER_MODEL_INPUT = Component.translatable("screen.openplayer.controls.provider_model_input");
    private static final Component PROVIDER_API_KEY_INPUT = Component.translatable("screen.openplayer.controls.provider_api_key_input");
    private static final Component PROFILE_ID_INPUT = Component.translatable("screen.openplayer.controls.profile_id_input");
    private static final Component PROFILE_DISPLAY_NAME_INPUT = Component.translatable("screen.openplayer.controls.profile_display_name_input");
    private static final Component PROFILE_DESCRIPTION_INPUT = Component.translatable("screen.openplayer.controls.profile_description_input");
    private static final Component PROFILE_SKIN_FILE_INPUT = Component.translatable("screen.openplayer.controls.profile_skin_file_input");
    private static final Component PROFILE_ROLE_INPUT = Component.translatable("screen.openplayer.controls.profile_role_input");
    private static final Component PROFILE_PROMPT_INPUT = Component.translatable("screen.openplayer.controls.profile_prompt_input");
    private static final Component PROFILE_SETTINGS_INPUT = Component.translatable("screen.openplayer.controls.profile_settings_input");
    private static final int BUTTON_WIDTH = 142;
    private static final int COMMAND_INPUT_WIDTH = 220;
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final int TAB_TOP = 42;
    private EditBox commandInput;
    private EditBox providerEndpointInput;
    private EditBox providerModelInput;
    private EditBox providerApiKeyInput;
    private EditBox profileIdInput;
    private EditBox profileDisplayNameInput;
    private EditBox profileDescriptionInput;
    private EditBox profileSkinFileInput;
    private EditBox profileRoleInput;
    private EditBox profilePromptInput;
    private EditBox profileSettingsInput;
    private String commandDraft = "";
    private String providerEndpointDraft = "";
    private String providerModelDraft = "";
    private String providerApiKeyDraft = "";
    private boolean clearApiKeyOnSave;
    private boolean profileAllowWorldActions;
    private boolean confirmDeleteSelected;
    private String selectedAssignmentId;
    private int selectedImportIndex;
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
                        rebuildControlWidgetsPreservingCommandText(true);
                    })
                    .bounds(margin, y, listWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build());
        }
        int pagerTop = 58 + visibleAssignments * (OpenPlayerControlLayout.BUTTON_HEIGHT + 4) + 2;
        if (page.pageCount() > 1) {
            int pagerButtonWidth = Math.max(45, (listWidth - 62) / 2);
            Button previous = Button.builder(Component.translatable("screen.openplayer.controls.previous_page"), button -> {
                        pageIndex = Math.max(0, pageIndex - 1);
                        rebuildControlWidgetsPreservingCommandText(false);
                    })
                    .bounds(margin, pagerTop, pagerButtonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                    .build();
            previous.active = page.hasPrevious();
            this.addRenderableWidget(previous);
            Button next = Button.builder(Component.translatable("screen.openplayer.controls.next_page"), button -> {
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
        } else if (controlPage == ControlPage.PROFILE) {
            addProfileWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.IMPORTS) {
            addImportWidgets(rightLeft, rightWidth);
        } else if (controlPage == ControlPage.MAIN) {
            addMainWidgets(rightLeft, rightWidth);
        }
    }

    private void addPageTabs(int rightLeft, int rightWidth) {
        int tabSpacing = 4;
        int tabWidth = Math.max(48, Math.min(72, (rightWidth - tabSpacing * (ControlPage.values().length - 1)) / ControlPage.values().length));
        int tabsLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, tabWidth * ControlPage.values().length + tabSpacing * (ControlPage.values().length - 1));
        ControlPage[] pages = ControlPage.values();
        for (int index = 0; index < pages.length; index++) {
            ControlPage page = pages[index];
            Button tab = Button.builder(Component.translatable(page.translationKey()), button -> {
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
        Button sendButton = Button.builder(Component.translatable("screen.openplayer.controls.send_to_selected"), button -> sendCommandText())
                .bounds(leftColumn, rowTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build();
        sendButton.active = hasSelection;
        this.addRenderableWidget(sendButton);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.profile_page"), button -> {
                    controlPage = ControlPage.PROFILE;
                    rebuildControlWidgetsPreservingCommandText(true);
                })
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
        this.addRenderableWidget(Button.builder(Component.translatable(clearApiKeyOnSave ? "screen.openplayer.controls.clear_key_on_save" : "screen.openplayer.controls.preserve_blank_key"), button -> {
                    clearApiKeyOnSave = !clearApiKeyOnSave;
                    rebuildControlWidgetsPreservingCommandText(true);
                })
                .bounds(controlsLeft, providerTop + rowStep * 3, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.openplayer.controls.save_provider_config"), button -> sendProviderConfig())
                .bounds(controlsLeft, providerTop + rowStep * 4, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    private void addProfileWidgets(int rightLeft, int rightWidth) {
        int inputWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, COMMAND_INPUT_WIDTH);
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
                    rebuildControlWidgetsPreservingCommandText(true);
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
                    rebuildControlWidgetsPreservingCommandText(true);
                })
                .bounds(buttonsLeft + buttonWidth + OpenPlayerControlLayout.BUTTON_SPACING, rowTop + rowStep * 9, buttonWidth, OpenPlayerControlLayout.BUTTON_HEIGHT)
                .build());
    }

    private EditBox profileInput(int left, int top, int width, int maxLength, Component hint, String value) {
        EditBox input = new EditBox(this.font, left, top, width, OpenPlayerControlLayout.BUTTON_HEIGHT, hint);
        input.setMaxLength(maxLength);
        input.setHint(hint);
        input.setValue(value == null ? "" : value);
        this.addRenderableWidget(input);
        return input;
    }

    private void addImportWidgets(int rightLeft, int rightWidth) {
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
        int listWidth = OpenPlayerControlLayout.clampedControlWidth(rightWidth, COMMAND_INPUT_WIDTH);
        int listLeft = OpenPlayerControlLayout.centeredLeft(rightLeft, rightWidth, listWidth);
        for (int index = 0; index < Math.min(imports.size(), 6); index++) {
            int importIndex = index;
            Button fileButton = Button.builder(Component.literal(fit((index == selectedImportIndex ? "> " : "") + imports.get(index), listWidth - 10)), button -> {
                        selectedImportIndex = importIndex;
                        rebuildControlWidgetsPreservingCommandText(true);
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

    private void renderProviderPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.provider_config"), left, top, width, 0xFFFFFF);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_key_preserve_note"), left, top + 150, width, 0xA0A0A0);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_key_safe_note"), left, top + 164, width, 0xA0A0A0);
    }

    private void renderProfilePage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.profile_manager"), left, top, width, 0xFFFFFF);
        if (protectedProfileSelected()) {
            drawFitted(graphics, tr("screen.openplayer.controls.default_profile_delete_note"), left, OpenPlayerControlLayout.PROFILE_DEFAULT_NOTE_TOP, width, 0xA0A0A0);
        }
        drawFitted(graphics, tr("screen.openplayer.controls.profile_file_status", OpenPlayerClientStatus.characterFileOperationStatus()), left, OpenPlayerControlLayout.PROFILE_STATUS_TOP, width, 0xC0C0C0);
    }

    private void renderImportPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.imports_title"), left, top, width, 0xFFFFFF);
        drawFitted(graphics, tr("screen.openplayer.controls.imports_hint"), left, top + 182, width, 0xA0A0A0);
        if (OpenPlayerClientStatus.importFileNames().isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_import_files"), left, top + 54, width, 0xA0A0A0);
        }
        drawFitted(graphics, tr("screen.openplayer.controls.profile_file_status", OpenPlayerClientStatus.characterFileOperationStatus()), left, top + 196, width, 0xC0C0C0);
    }

    private void renderStatusPage(GuiGraphics graphics, int left, int top, int width) {
        drawFitted(graphics, tr("screen.openplayer.controls.status_automation", OpenPlayerClientStatus.automationStatus()), left, top, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_parser", OpenPlayerClientStatus.parserStatus()), left, top + 14, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_endpoint", OpenPlayerClientStatus.endpointStatus()), left, top + 28, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_model", OpenPlayerClientStatus.modelStatus()), left, top + 42, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_api_key", OpenPlayerClientStatus.apiKeyStatus()), left, top + 56, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.status_character_files", OpenPlayerClientStatus.characterFileOperationStatus()), left, top + 70, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.provider_key_preserve_note"), left, top + 92, width, 0xA0A0A0);
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_selected_assignment"), left, top + 114, width, 0xFFFFFF);
            return;
        }
        drawFitted(graphics, tr("screen.openplayer.controls.selected_value", selected.displayName()), left, top + 114, width, lifecycleColor(selected.lifecycleStatus()));
        drawFitted(graphics, tr("screen.openplayer.controls.description_value", selected.description().isEmpty() ? tr("screen.openplayer.controls.none") : selected.description()), left, top + 128, width, 0xC0C0C0);
        drawFitted(graphics, tr("screen.openplayer.controls.skin_value", selected.skinStatus()), left, top + 142, width, 0xC0C0C0);
        int eventTop = top + 156;
        if (selected.conversationEvents().isEmpty()) {
            drawFitted(graphics, tr("screen.openplayer.controls.no_spoken_status"), left, eventTop, width, 0xA0A0A0);
            return;
        }
        drawFitted(graphics, tr("screen.openplayer.controls.spoken_status", selected.conversationEvents().get(selected.conversationEvents().size() - 1)), left, eventTop, width, 0xD0D0D0);
    }

    private void renderCharacterMessages(GuiGraphics graphics, int left, int top, int width) {
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

    private void renderSelectedDetails(GuiGraphics graphics, int left, int top, int width) {
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

    private String tr(String key, Object... values) {
        return Component.translatable(key, values).getString();
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

    private void sendSpawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendSpawnRequest(selected.assignmentId());
        }
    }

    private void sendDespawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendDespawnRequest(selected.assignmentId());
        }
    }

    private void sendFollowToggle() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return;
        }
        if (followingSelected()) {
            OpenPlayerRequestSender.sendStopRequest(selected.assignmentId());
        } else {
            OpenPlayerRequestSender.sendFollowOwnerRequest(selected.assignmentId());
        }
    }

    private void sendStop() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendStopRequest(selected.assignmentId());
        }
    }

    private void sendCommandText() {
        String value = commandInput.getValue().trim();
        if (!value.isEmpty()) {
            LocalCharacterListEntry selected = selectedCharacter();
            if (selected != null) {
                OpenPlayerRequestSender.sendCommandTextRequest(selected.assignmentId(), value);
                commandInput.setValue("");
                commandDraft = "";
            }
        }
    }

    private void sendProfileSave() {
        try {
            OpenPlayerRequestSender.sendCharacterSaveRequest(new LocalCharacterDefinition(
                    profileIdInput.getValue(),
                    profileDisplayNameInput.getValue(),
                    profileDescriptionInput.getValue(),
                    null,
                    profileSkinFileInput.getValue(),
                    profileRoleInput.getValue(),
                    profilePromptInput.getValue(),
                    profileSettingsInput.getValue(),
                    profileAllowWorldActions
            ));
            confirmDeleteSelected = false;
        } catch (IllegalArgumentException exception) {
            OpenPlayerClientStatus.updateCharacterFileOperationStatus(exception.getMessage());
        }
    }

    private void duplicateSelectedProfile() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return;
        }
        selectedAssignmentId = null;
        rebuildControlWidgetsPreservingCommandText(true);
        profileIdInput.setValue(copyId(selected.characterId()));
        profileDisplayNameInput.setValue(Component.translatable("screen.openplayer.controls.duplicate_profile_display_name", selected.displayName()).getString());
        profileDescriptionInput.setValue(selected.description());
        profileSkinFileInput.setValue(selected.localSkinFile());
        profileRoleInput.setValue(selected.defaultRoleId());
        profilePromptInput.setValue(selected.conversationPrompt());
        profileSettingsInput.setValue(selected.conversationSettings());
        profileAllowWorldActions = selected.allowWorldActions();
    }

    private void sendProfileDelete() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null || protectedProfileSelected()) {
            return;
        }
        if (!confirmDeleteSelected) {
            confirmDeleteSelected = true;
            rebuildControlWidgetsPreservingCommandText(true);
            return;
        }
        OpenPlayerRequestSender.sendCharacterDeleteRequest(selected.characterId());
        selectedAssignmentId = null;
        confirmDeleteSelected = false;
    }

    private void sendSelectedImport() {
        List<String> imports = OpenPlayerClientStatus.importFileNames();
        if (imports.isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(selectedImportIndex, imports.size() - 1));
        OpenPlayerRequestSender.sendCharacterImportRequest(imports.get(safeIndex));
    }

    private void openImportsFolder() {
        try {
            Files.createDirectories(OpenPlayerLocalCharacters.importsDirectory());
            Util.getPlatform().openFile(OpenPlayerLocalCharacters.importsDirectory().toFile());
        } catch (IOException exception) {
            OpenPlayerClientStatus.updateCharacterFileOperationStatus(Component.translatable("screen.openplayer.controls.open_imports_folder_failed").getString());
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

    private boolean followingSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return false;
        }
        String normalized = selected.lifecycleStatus().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("follow");
    }

    private boolean protectedProfileSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        return selected != null && "openplayer_default".equals(selected.characterId());
    }

    private String newProfileId() {
        int suffix = OpenPlayerClientStatus.characters().size() + 1;
        return "companion_" + suffix;
    }

    private String copyId(String characterId) {
        String base = characterId == null || characterId.isBlank() ? "companion" : characterId;
        String candidate = base + "_copy";
        int suffix = 2;
        while (characterIdExists(candidate)) {
            candidate = base + "_copy_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean characterIdExists(String characterId) {
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            if (character.characterId().equals(characterId)) {
                return true;
            }
        }
        return false;
    }

    private String lifecycleLabel(String lifecycleStatus) {
        String normalized = lifecycleStatus.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spawn") || normalized.contains("active")) {
            return Component.translatable("screen.openplayer.controls.lifecycle.active").getString();
        }
        if (normalized.contains("despawn")) {
            return Component.translatable("screen.openplayer.controls.lifecycle.despawned").getString();
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
        MAIN("screen.openplayer.controls.tab.main"),
        PROFILE("screen.openplayer.controls.tab.profile"),
        IMPORTS("screen.openplayer.controls.tab.imports"),
        PROVIDER("screen.openplayer.controls.tab.provider"),
        STATUS("screen.openplayer.controls.tab.status");

        private final String translationKey;

        ControlPage(String translationKey) {
            this.translationKey = translationKey;
        }

        private String translationKey() {
            return translationKey;
        }
    }
}
