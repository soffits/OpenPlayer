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


abstract class OpenPlayerControlScreenBase extends Screen {
    protected static final Component TITLE = Component.translatable("screen.openplayer.controls.title");
    protected static final Component PROVIDER_ENDPOINT_INPUT = Component.translatable("screen.openplayer.controls.provider_endpoint_input");
    protected static final Component PROVIDER_MODEL_INPUT = Component.translatable("screen.openplayer.controls.provider_model_input");
    protected static final Component PROVIDER_API_KEY_INPUT = Component.translatable("screen.openplayer.controls.provider_api_key_input");
    protected static final Component PROFILE_ID_INPUT = Component.translatable("screen.openplayer.controls.profile_id_input");
    protected static final Component PROFILE_DISPLAY_NAME_INPUT = Component.translatable("screen.openplayer.controls.profile_display_name_input");
    protected static final Component PROFILE_DESCRIPTION_INPUT = Component.translatable("screen.openplayer.controls.profile_description_input");
    protected static final Component PROFILE_SKIN_FILE_INPUT = Component.translatable("screen.openplayer.controls.profile_skin_file_input");
    protected static final Component PROFILE_ROLE_INPUT = Component.translatable("screen.openplayer.controls.profile_role_input");
    protected static final Component PROFILE_PROMPT_INPUT = Component.translatable("screen.openplayer.controls.profile_prompt_input");
    protected static final Component PROFILE_SETTINGS_INPUT = Component.translatable("screen.openplayer.controls.profile_settings_input");
    protected static final int BUTTON_WIDTH = 142;
    protected static final int CONTROL_INPUT_WIDTH = 220;
    protected static final int TAB_TOP = 42;
    protected EditBox providerEndpointInput;
    protected EditBox providerModelInput;
    protected EditBox providerApiKeyInput;
    protected EditBox profileIdInput;
    protected EditBox profileDisplayNameInput;
    protected EditBox profileDescriptionInput;
    protected EditBox profileSkinFileInput;
    protected EditBox profileRoleInput;
    protected EditBox profilePromptInput;
    protected EditBox profileSettingsInput;
    protected String providerEndpointDraft = "";
    protected String providerModelDraft = "";
    protected String providerApiKeyDraft = "";
    protected String lastHydratedProviderEndpoint = "";
    protected String lastHydratedProviderModel = "";
    protected long hydratedProviderStatusVersion = -1L;
    protected boolean clearApiKeyOnSave;
    protected boolean profileAllowWorldActions;
    protected boolean confirmDeleteSelected;
    protected String selectedAssignmentId;
    protected int selectedImportIndex;
    protected String renderedCharacterKey = "";
    protected int pageIndex;
    protected ControlPage controlPage = ControlPage.MAIN;

    protected OpenPlayerControlScreenBase() {
        super(TITLE);
    }

    protected void sendExportSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendCharacterExportRequest(selected.characterId());
        }
    }

    protected void sendSpawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendSpawnRequest(selected.assignmentId());
            requestStatusForSelection();
        }
    }

    protected void sendDespawn() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendDespawnRequest(selected.assignmentId());
            requestStatusForSelection();
        }
    }

    protected void sendFollowToggle() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return;
        }
        if (followingSelected()) {
            OpenPlayerRequestSender.sendStopRequest(selected.assignmentId());
        } else {
            OpenPlayerRequestSender.sendFollowOwnerRequest(selected.assignmentId());
        }
        requestStatusForSelection();
    }

    protected void sendStop() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected != null) {
            OpenPlayerRequestSender.sendStopRequest(selected.assignmentId());
            requestStatusForSelection();
        }
    }

    protected void sendProfileSave() {
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

    protected void duplicateSelectedProfile() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return;
        }
        selectedAssignmentId = null;
        rebuildControlWidgetsPreservingDrafts(true);
        profileIdInput.setValue(copyId(selected.characterId()));
        profileDisplayNameInput.setValue(Component.translatable("screen.openplayer.controls.duplicate_profile_display_name", selected.displayName()).getString());
        profileDescriptionInput.setValue(selected.description());
        profileSkinFileInput.setValue(selected.localSkinFile());
        profileRoleInput.setValue(selected.defaultRoleId());
        profilePromptInput.setValue(selected.conversationPrompt());
        profileSettingsInput.setValue(selected.conversationSettings());
        profileAllowWorldActions = selected.allowWorldActions();
    }

    protected void sendProfileDelete() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null || protectedProfileSelected()) {
            return;
        }
        if (!confirmDeleteSelected) {
            confirmDeleteSelected = true;
            rebuildControlWidgetsPreservingDrafts(true);
            return;
        }
        OpenPlayerRequestSender.sendCharacterDeleteRequest(selected.characterId());
        selectedAssignmentId = null;
        confirmDeleteSelected = false;
    }

    protected void sendSelectedImport() {
        List<String> imports = OpenPlayerClientStatus.importFileNames();
        if (imports.isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(selectedImportIndex, imports.size() - 1));
        OpenPlayerRequestSender.sendCharacterImportRequest(imports.get(safeIndex));
    }

    protected void openImportsFolder() {
        try {
            Files.createDirectories(OpenPlayerLocalCharacters.importsDirectory());
            Util.getPlatform().openFile(OpenPlayerLocalCharacters.importsDirectory().toFile());
        } catch (IOException exception) {
            OpenPlayerClientStatus.updateCharacterFileOperationStatus(Component.translatable("screen.openplayer.controls.open_imports_folder_failed").getString());
        }
    }

    protected void sendProviderConfig() {
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

    protected void hydrateProviderDraftsFromStatus() {
        long statusVersion = OpenPlayerClientStatus.providerStatusVersion();
        if (hydratedProviderStatusVersion == statusVersion) {
            return;
        }
        String endpointValue = OpenPlayerClientStatus.providerEndpointValue();
        String modelValue = OpenPlayerClientStatus.providerModelValue();
        if (providerEndpointDraft.isBlank() || providerEndpointDraft.equals(lastHydratedProviderEndpoint)) {
            providerEndpointDraft = endpointValue;
        }
        if (providerModelDraft.isBlank() || providerModelDraft.equals(lastHydratedProviderModel)) {
            providerModelDraft = modelValue;
        }
        lastHydratedProviderEndpoint = endpointValue;
        lastHydratedProviderModel = modelValue;
        hydratedProviderStatusVersion = statusVersion;
    }

    protected void sendProviderTest() {
        OpenPlayerClientStatus.updateProviderTestResult("running", "");
        OpenPlayerRequestSender.sendProviderTestRequest();
    }

    protected LocalCharacterListEntry selectedCharacter() {
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

    protected void requestStatusForSelection() {
        OpenPlayerRequestSender.sendStatusRequest(selectedAssignmentId);
    }

    protected String characterKey() {
        List<String> parts = new ArrayList<>();
        parts.add(OpenPlayerClientStatus.characterListStatus());
        parts.add(OpenPlayerClientStatus.parserStatus());
        parts.add(OpenPlayerClientStatus.endpointStatus());
        parts.add(OpenPlayerClientStatus.providerEndpointValue());
        parts.add(OpenPlayerClientStatus.modelStatus());
        parts.add(OpenPlayerClientStatus.providerModelValue());
        parts.add(OpenPlayerClientStatus.apiKeyStatus());
        parts.add(OpenPlayerClientStatus.providerTestStatus());
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            parts.add(character.assignmentId() + ":" + character.lifecycleStatus() + ":" + character.conversationStatus());
            parts.addAll(character.conversationEvents());
        }
        parts.addAll(OpenPlayerClientStatus.characterErrors());
        return String.join("|", parts);
    }

    protected int pageIndexForRebuild(List<LocalCharacterListEntry> characters, int visibleAssignments, boolean keepSelectedVisible) {
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

    protected int visibleAssignmentCount() {
        return OpenPlayerControlLayout.visibleAssignmentCount(this.height);
    }

    protected String galleryButtonLabel(LocalCharacterListEntry character) {
        String selectedPrefix = character.assignmentId().equals(selectedAssignmentId) ? "> " : "";
        return selectedPrefix + character.displayName() + " [" + lifecycleLabel(character.lifecycleStatus()) + "]";
    }

    protected boolean followingSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        if (selected == null) {
            return false;
        }
        String normalized = selected.lifecycleStatus().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("follow");
    }

    protected boolean protectedProfileSelected() {
        LocalCharacterListEntry selected = selectedCharacter();
        return selected != null && "openplayer_default".equals(selected.characterId());
    }

    protected String newProfileId() {
        int suffix = OpenPlayerClientStatus.characters().size() + 1;
        return "companion_" + suffix;
    }

    protected String copyId(String characterId) {
        String base = characterId == null || characterId.isBlank() ? "companion" : characterId;
        String candidate = base + "_copy";
        int suffix = 2;
        while (characterIdExists(candidate)) {
            candidate = base + "_copy_" + suffix;
            suffix++;
        }
        return candidate;
    }

    protected boolean characterIdExists(String characterId) {
        for (LocalCharacterListEntry character : OpenPlayerClientStatus.characters()) {
            if (character.characterId().equals(characterId)) {
                return true;
            }
        }
        return false;
    }

    protected String lifecycleLabel(String lifecycleStatus) {
        String normalized = lifecycleStatus.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spawn") || normalized.contains("active")) {
            return Component.translatable("screen.openplayer.controls.lifecycle.active").getString();
        }
        if (normalized.contains("despawn")) {
            return Component.translatable("screen.openplayer.controls.lifecycle.despawned").getString();
        }
        return lifecycleStatus;
    }

    protected int lifecycleColor(String lifecycleStatus) {
        String normalized = lifecycleStatus.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spawn") || normalized.contains("active")) {
            return 0x80FF80;
        }
        if (normalized.contains("despawn")) {
            return 0xC0C0C0;
        }
        return 0xFFD080;
    }

    protected String fit(String value, int width) {
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

    protected enum ControlPage {
        MAIN("screen.openplayer.controls.tab.main"),
        PROFILE("screen.openplayer.controls.tab.profile"),
        IMPORTS("screen.openplayer.controls.tab.imports"),
        PROVIDER("screen.openplayer.controls.tab.provider"),
        STATUS("screen.openplayer.controls.tab.status");

        protected final String translationKey;

        ControlPage(String translationKey) {
            this.translationKey = translationKey;
        }

        protected String translationKey() {
            return translationKey;
        }
    }

    protected abstract void rebuildControlWidgets(boolean keepSelectedVisible);

    protected abstract void rebuildControlWidgetsPreservingDrafts(boolean keepSelectedVisible);
}
