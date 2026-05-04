package dev.soffits.openplayer.network;

import dev.soffits.openplayer.conversation.ConversationReplyText;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentProviderException;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;

public final class OpenPlayerNetworkingTest {
    private OpenPlayerNetworkingTest() {
    }

    public static void main(String[] args) {
        acceptsSingleplayerOwnerProviderConfigSave();
        acceptsPermittedProviderConfigSave();
        rejectsUnauthorizedProviderConfigSave();
        acceptsSingleplayerOwnerLocalProfileManagement();
        acceptsPermittedLocalProfileManagement();
        rejectsUnauthorizedLocalProfileManagement();
        classifiesProviderHttpFailure();
        buildsBlankShortcutInstructions();
        reportsGenericCapabilityStatusLines();
        boundsPacketStringsForNetworkUtf();
    }

    private static void acceptsSingleplayerOwnerProviderConfigSave() {
        require(OpenPlayerNetworking.maySaveProviderConfig(true, false), "singleplayer owner must be allowed to save provider config");
    }

    private static void acceptsPermittedProviderConfigSave() {
        require(OpenPlayerNetworking.maySaveProviderConfig(false, true), "permitted player must be allowed to save provider config");
    }

    private static void rejectsUnauthorizedProviderConfigSave() {
        require(!OpenPlayerNetworking.maySaveProviderConfig(false, false), "unauthorized player must not save provider config");
    }

    private static void acceptsSingleplayerOwnerLocalProfileManagement() {
        require(OpenPlayerNetworking.mayManageLocalProfiles(true, false), "singleplayer owner must be allowed to manage local profiles");
    }

    private static void acceptsPermittedLocalProfileManagement() {
        require(OpenPlayerNetworking.mayManageLocalProfiles(false, true), "permitted player must be allowed to manage local profiles");
    }

    private static void rejectsUnauthorizedLocalProfileManagement() {
        require(!OpenPlayerNetworking.mayManageLocalProfiles(false, false), "unauthorized player must not manage local profiles");
    }

    private static void classifiesProviderHttpFailure() {
        IntentParseException exception = new IntentParseException(
                "intent provider failed",
                new IntentProviderException("intent provider request failed with status 401")
        );
        require("http_status".equals(OpenPlayerNetworking.providerFailureCode(exception)), "HTTP provider failure must be classified");
        require("401".equals(OpenPlayerNetworking.providerFailureDetail(exception)), "HTTP status must be preserved without response body");
    }

    private static void buildsBlankShortcutInstructions() {
        require(OpenPlayerNetworking.shortcutIntent(IntentKind.STOP).instruction().isEmpty(),
                "STOP shortcut must submit a blank instruction");
        require(OpenPlayerNetworking.shortcutIntent(IntentKind.FOLLOW_OWNER).instruction().isEmpty(),
                "FOLLOW_OWNER shortcut must submit a blank instruction");
        require(RuntimeIntentValidator.validate(OpenPlayerNetworking.shortcutIntent(IntentKind.STOP), true).isAccepted(),
                "STOP shortcut must pass runtime validation");
        require(RuntimeIntentValidator.validate(OpenPlayerNetworking.shortcutIntent(IntentKind.FOLLOW_OWNER), true).isAccepted(),
                "FOLLOW_OWNER shortcut must pass runtime validation");
    }

    private static void reportsGenericCapabilityStatusLines() {
        java.util.List<String> lines = OpenPlayerNetworking.capabilityStatusLines(null);
        require(!lines.isEmpty(), "capability status lines must not be empty");
        require(lines.stream().allMatch(line -> line.length() <= OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH),
                "capability status lines must fit the network packet limit");
        require(lines.get(0).contains("source=current_viewer_world"),
                "runtime status must truthfully label viewer/world source");
        require(lines.get(0).contains("current_dimension=unknown"),
                "runtime status must preserve viewer dimension information when unknown");
        require(lines.get(0).contains("inventory_source=not_reported"),
                "runtime status must not mislabel viewer inventory as NPC inventory");
        String overworldLine = OpenPlayerNetworking.runtimeStatusLine("minecraft:overworld");
        require(overworldLine.length() <= OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH,
                "normal viewer dimension status must fit the network packet limit");
        require(overworldLine.contains("current_dimension=minecraft:overworld"),
                "normal viewer dimension status must preserve dimension information");
        String joined = String.join("\n", lines).toLowerCase(java.util.Locale.ROOT);
        require(joined.contains("capability_report"), "status must include capability registry report");
    }

    private static void boundsPacketStringsForNetworkUtf() {
        String boundedNull = OpenPlayerNetworking.boundedPacketString(null, OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH);
        require(boundedNull.isEmpty(), "null packet strings must become empty strings");
        String longDimensionLine = "runtime_status source=current_viewer_world current_dimension=modded:"
                + "dimension_name_with_a_deliberately_long_namespace_and_path_to_cover_custom_world_ids"
                + " inventory_source=not_reported selected_npc=runtime_snapshot";
        String boundedLine = OpenPlayerNetworking.boundedPacketString(longDimensionLine,
                OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH);
        String boundedRuntimeStatusLine = OpenPlayerNetworking.runtimeStatusLine("modded:"
                + "dimension_name_with_a_deliberately_long_namespace_and_path_to_cover_custom_world_ids");
        require(boundedLine.length() <= OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH,
                "long dynamic status lines must fit the network packet limit");
        require(boundedRuntimeStatusLine.length() <= OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH,
                "long runtime dimension status must fit the network packet limit");
        require(boundedLine.endsWith(ConversationReplyText.TRUNCATION_SUFFIX),
                "truncated packet strings must have a deterministic suffix");
        require(OpenPlayerNetworking.boundedPacketString(longDimensionLine, 3).equals("..."),
                "small packet bounds must still use the deterministic truncation suffix safely");
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeUtf(boundedLine, OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH);
        require(buffer.readUtf(OpenPlayerNetworking.STATUS_LINE_NETWORK_MAX_LENGTH).equals(boundedLine),
                "bounded packet strings must round-trip through FriendlyByteBuf");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
