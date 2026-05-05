package dev.soffits.openplayer.runtime.resource;

import dev.soffits.openplayer.OpenPlayerConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ResourceDependencyPlanner {
    private static final List<String> PICKAXES = List.of(
            OpenPlayerConstants.MINECRAFT_WOODEN_PICKAXE_ID,
            OpenPlayerConstants.MINECRAFT_STONE_PICKAXE_ID,
            OpenPlayerConstants.MINECRAFT_IRON_PICKAXE_ID,
            OpenPlayerConstants.MINECRAFT_DIAMOND_PICKAXE_ID,
            OpenPlayerConstants.MINECRAFT_NETHERITE_PICKAXE_ID
    );

    public ResourcePlanResult planCraftAndDeliver(String itemId, int count, String targetPlayer,
                                                  ResourcePlanObservation observation) {
        if (itemId == null || itemId.isBlank() || count < 1 || observation == null) {
            throw new IllegalArgumentException("invalid resource plan request");
        }
        ResourcePlanStep craft = craftStep(itemId.trim(), count, observation);
        ResourcePlanStep delivery = deliveryStep(itemId.trim(), count, targetPlayer, observation);
        ResourcePlanStep root = new ResourcePlanStep(
                ResourcePlanKind.PREREQUISITE, itemId.trim(), count, "resource_plan", List.of(), 0, List.of(craft, delivery)
        );
        List<String> missing = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        collectMissingAndBlockers(root, missing, blockers);
        return new ResourcePlanResult(root, nextReadyLeaf(root), missing, blockers, false);
    }

    public static Map<String, ResourceRecipe> builtinRecipes() {
        LinkedHashMap<String, ResourceRecipe> recipes = new LinkedHashMap<>();
        recipes.put(OpenPlayerConstants.MINECRAFT_FURNACE_ID, new ResourceRecipe(
                OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1,
                Map.of(OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID, 8), true, true
        ));
        return Map.copyOf(recipes);
    }

    private ResourcePlanStep craftStep(String itemId, int count, ResourcePlanObservation observation) {
        if (observation.count(itemId) >= count) {
            return new ResourcePlanStep(ResourcePlanKind.CRAFT, itemId, count, "inventory_verified", List.of(),
                    observation.failCount("craft:" + itemId), List.of());
        }
        ResourceRecipe recipe = observation.recipes().get(itemId);
        if (recipe == null) {
            return new ResourcePlanStep(ResourcePlanKind.CRAFT, itemId, count, "recipes_for", List.of("missing recipe adapter or recipe for " + itemId),
                    observation.failCount("craft:" + itemId), List.of());
        }
        List<ResourcePlanStep> children = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        if (!observation.craftingAdapterAvailable() || !recipe.adapterAvailable()) {
            blockers.add("missing crafting execution adapter");
        }
        if (recipe.requiresCraftingTable() && observation.count(OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID) < 1
                && !observation.visibleLoadedBlocks().contains(OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID)) {
            children.add(new ResourcePlanStep(ResourcePlanKind.PREREQUISITE,
                    OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID, 1, "locate_or_craft_workstation",
                    List.of("missing crafting table workstation"), observation.failCount("workstation:crafting_table"), List.of()));
        }
        int craftsNeeded = (int) Math.ceil((double) Math.max(0, count - observation.count(itemId)) / recipe.outputCount());
        for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
            int required = input.getValue() * craftsNeeded;
            int carried = observation.count(input.getKey());
            if (carried < required) {
                children.add(materialStep(input.getKey(), required - carried, observation));
            }
        }
        return new ResourcePlanStep(ResourcePlanKind.CRAFT, itemId, count, "craft", blockers,
                observation.failCount("craft:" + itemId), children);
    }

    private ResourcePlanStep materialStep(String itemId, int missingCount, ResourcePlanObservation observation) {
        if (OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID.equals(itemId)) {
            List<ResourcePlanStep> children = new ArrayList<>();
            if (!hasAnyPickaxe(observation)) {
                children.add(new ResourcePlanStep(ResourcePlanKind.PREREQUISITE, "minecraft:pickaxe", 1,
                        "craft_or_collect_tool", List.of("missing pickaxe required for stone drops"),
                        observation.failCount("tool:pickaxe"), List.of()));
            }
            List<String> blockers = new ArrayList<>();
            if (!observation.policyAllowsWorldActions()) {
                blockers.add("policy denies block collection");
            }
            if (!observation.collectBlockAdapterAvailable()) {
                blockers.add("missing collect block adapter");
            }
            String primitive = observation.visibleLoadedBlocks().contains(OpenPlayerConstants.MINECRAFT_STONE_ID)
                    ? "break_block_at" : "find_loaded_blocks";
            return new ResourcePlanStep(ResourcePlanKind.COLLECT_BLOCK, itemId, missingCount, primitive, blockers,
                    observation.failCount("collect_block:" + itemId), children);
        }
        return new ResourcePlanStep(ResourcePlanKind.COLLECT_ITEM, itemId, missingCount, "pickup_items_nearby",
                List.of("missing generic acquisition plan for " + itemId), observation.failCount("collect_item:" + itemId), List.of());
    }

    private ResourcePlanStep deliveryStep(String itemId, int count, String targetPlayer, ResourcePlanObservation observation) {
        List<String> blockers = new ArrayList<>();
        if (targetPlayer == null || targetPlayer.isBlank()) {
            blockers.add("missing delivery target player");
        }
        if (observation.count(itemId) < count) {
            blockers.add("delivery item not yet in NPC inventory");
        }
        if (!observation.deliveryAdapterAvailable()) {
            blockers.add("missing delivery verification adapter");
        }
        return new ResourcePlanStep(ResourcePlanKind.DELIVERY, itemId, count, "drop_item", blockers,
                observation.failCount("delivery:" + itemId), List.of());
    }

    private static boolean hasAnyPickaxe(ResourcePlanObservation observation) {
        for (String pickaxe : PICKAXES) {
            if (observation.count(pickaxe) > 0) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ResourcePlanStep> nextReadyLeaf(ResourcePlanStep step) {
        if (step.blockers().isEmpty() && step.children().isEmpty()) {
            return "inventory_verified".equals(step.primitive()) ? Optional.empty() : Optional.of(step);
        }
        for (ResourcePlanStep child : step.children()) {
            Optional<ResourcePlanStep> next = nextReadyLeaf(child);
            if (next.isPresent()) {
                return next;
            }
        }
        return Optional.empty();
    }

    private static void collectMissingAndBlockers(ResourcePlanStep step, List<String> missing, List<String> blockers) {
        for (String blocker : step.blockers()) {
            blockers.add(step.kind().name().toLowerCase(java.util.Locale.ROOT) + ":" + blocker);
            if (blocker.startsWith("delivery item not yet") || blocker.startsWith("missing visible loaded")
                    || blocker.startsWith("missing generic acquisition")) {
                missing.add(step.targetId() + " x" + step.count());
            }
        }
        for (ResourcePlanStep child : step.children()) {
            collectMissingAndBlockers(child, missing, blockers);
        }
    }
}
