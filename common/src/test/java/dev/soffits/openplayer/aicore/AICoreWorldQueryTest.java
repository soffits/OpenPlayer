package dev.soffits.openplayer.aicore;

import java.util.List;
import java.util.Map;

public final class AICoreWorldQueryTest {
    private AICoreWorldQueryTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireTool("block_at");
        AICoreTestSupport.requireTool("can_see_block");
        ToolResult missingAdapter = MinecraftPrimitiveTools.validate(ToolCall.of("block_at_cursor", new ToolArguments(Map.of("maxDistance", "32"))), new ToolValidationContext(true));
        AICoreTestSupport.require(missingAdapter.status() == ToolResultStatus.SUCCESS, "block_at_cursor must validate after raycast adapter wiring");
        ToolResult entityCursor = MinecraftPrimitiveTools.validate(ToolCall.of("block_at_entity_cursor", new ToolArguments(Map.of("entityId", "entity-1", "maxDistance", "32"))), new ToolValidationContext(true));
        AICoreTestSupport.require(entityCursor.status() == ToolResultStatus.SUCCESS, "block_at_entity_cursor must validate after entity cursor adapter wiring");
        ToolResult unbounded = MinecraftPrimitiveTools.validate(ToolCall.of("find_block", new ToolArguments(Map.of("matching", "minecraft:dirt"))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(unbounded, "Missing required argument: maxDistance");
        raycastSelectsFirstLoadedBlock();
        visibilityStopsAtFirstSolidLoadedBlock();
        entityCursorSelectsNearestRayHit();
        entityCursorToolUsesEntityViewVector();
        entityCursorToolRejectsMissingEntityViewVector();
    }

    private static void raycastSelectsFirstLoadedBlock() {
        AICoreWorldQueryAdapter adapter = new AICoreWorldQueryAdapter(List.of(
                new AICoreBlockSnapshot("minecraft:stone", new AICoreVec3(0.0D, 1.0D, 4.0D), true),
                new AICoreBlockSnapshot("minecraft:dirt", new AICoreVec3(0.0D, 1.0D, 6.0D), true)
        ), List.of());
        ToolResult result = adapter.execute(ToolCall.of("block_at_cursor", new ToolArguments(Map.of("maxDistance", "10"))),
                new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(0.0D, 0.0D, 1.0D));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "raycast must hit loaded block");
        AICoreTestSupport.require("minecraft:stone".equals(result.details().get("resourceId")), "raycast must return first loaded block");
    }

    private static void visibilityStopsAtFirstSolidLoadedBlock() {
        AICoreWorldQueryAdapter adapter = new AICoreWorldQueryAdapter(List.of(
                new AICoreBlockSnapshot("minecraft:stone", new AICoreVec3(0.0D, 1.0D, 2.0D), true),
                new AICoreBlockSnapshot("minecraft:diamond_block", new AICoreVec3(0.0D, 1.0D, 4.0D), true)
        ), List.of());
        ToolResult result = adapter.execute(ToolCall.of("can_see_block", new ToolArguments(Map.of("x", "0", "y", "1", "z", "4"))),
                new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(0.0D, 0.0D, 1.0D));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "can_see_block must return a truthful boolean result");
        AICoreTestSupport.require("false".equals(result.details().get("visible")), "occluded target must not be visible");
    }

    private static void entityCursorSelectsNearestRayHit() {
        AICoreWorldQueryAdapter adapter = new AICoreWorldQueryAdapter(List.of(), List.of(
                new AICoreEntitySnapshot("far", "minecraft:zombie", new AICoreVec3(0.5D, 1.5D, 6.0D)),
                new AICoreEntitySnapshot("near", "minecraft:skeleton", new AICoreVec3(0.5D, 1.5D, 3.0D))
        ));
        ToolResult result = adapter.execute(ToolCall.of("entity_at_cursor", new ToolArguments(Map.of("maxDistance", "8"))),
                new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(0.0D, 0.0D, 1.0D));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "entity raycast must hit loaded entity");
        AICoreTestSupport.require("near".equals(result.details().get("id")), "entity raycast must select nearest cursor hit");
    }

    private static void entityCursorToolUsesEntityViewVector() {
        AICoreWorldQueryAdapter adapter = new AICoreWorldQueryAdapter(List.of(
                new AICoreBlockSnapshot("minecraft:stone", new AICoreVec3(0.0D, 1.0D, 4.0D), true),
                new AICoreBlockSnapshot("minecraft:dirt", new AICoreVec3(4.0D, 1.0D, 0.0D), true)
        ), List.of(
                new AICoreEntitySnapshot("entity-1", "minecraft:zombie", new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(1.0D, 0.0D, 0.0D))
        ));
        ToolResult result = adapter.execute(ToolCall.of("block_at_entity_cursor", new ToolArguments(Map.of("entityId", "entity-1", "maxDistance", "10"))),
                new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(0.0D, 0.0D, 1.0D));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "pure adapter must execute entity cursor when entity view is present");
        AICoreTestSupport.require("minecraft:dirt".equals(result.details().get("resourceId")), "entity cursor must use entityId view vector, not NPC cursor");
    }

    private static void entityCursorToolRejectsMissingEntityViewVector() {
        AICoreWorldQueryAdapter adapter = new AICoreWorldQueryAdapter(List.of(
                new AICoreBlockSnapshot("minecraft:stone", new AICoreVec3(0.0D, 1.0D, 4.0D), true)
        ), List.of(
                new AICoreEntitySnapshot("entity-1", "minecraft:zombie", new AICoreVec3(0.5D, 1.5D, 0.5D))
        ));
        ToolResult result = adapter.execute(ToolCall.of("block_at_entity_cursor", new ToolArguments(Map.of("entityId", "entity-1", "maxDistance", "10"))),
                new AICoreVec3(0.5D, 1.5D, 0.5D), new AICoreVec3(0.0D, 0.0D, 1.0D));
        AICoreTestSupport.require(result.status() == ToolResultStatus.FAILED, "pure adapter must reject entity cursor without entity view state");
        AICoreTestSupport.require("unsupported_missing_entity_cursor_view_adapter".equals(result.reason()), "missing entity view state must report precise adapter reason");
    }
}
