package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.aicore.CapabilityScopedToolDocs;
import dev.soffits.openplayer.runtime.objective.CraftingObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.DeliveryObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.ObjectiveProgress;
import dev.soffits.openplayer.runtime.objective.SmeltingObjectiveValidator;
import dev.soffits.openplayer.runtime.profile.ModelRoleConfig;
import dev.soffits.openplayer.runtime.resource.ResourceDependencyPlanner;
import dev.soffits.openplayer.runtime.resource.ResourcePlanKind;
import dev.soffits.openplayer.runtime.resource.ResourcePlanObservation;
import dev.soffits.openplayer.runtime.resource.ResourcePlanResult;
import dev.soffits.openplayer.runtime.resource.ResourceRecipe;
import java.util.Map;
import java.util.Set;

public final class PhaseThreeResourcePlanningTest {
    private PhaseThreeResourcePlanningTest() {
    }

    public static void main(String[] args) {
        furnacePlanDecomposesIntoCobblestoneToolWorkstationAndDelivery();
        furnacePlanUpdatesNextLeafAfterMaterialsArrive();
        furnacePlanMovesToDeliveryAfterCraftedItemExists();
        craftingValidatorReportsRecipeWorkstationAndInputState();
        smeltingValidatorReportsMissingAdapterFuelInputOutputAndProgress();
        deliveryValidatorRequiresObservedInventoryOrVisibleCollectableDrop();
        scopedToolDocsForegroundFurnaceCapabilitiesAndSuppressUnrelatedTools();
        modelRolesAreRedactedAndCannotBypassValidators();
    }

    private static void furnacePlanDecomposesIntoCobblestoneToolWorkstationAndDelivery() {
        ResourceDependencyPlanner planner = new ResourceDependencyPlanner();
        ResourcePlanObservation observation = observation(
                Map.of(), Set.of(), true, true, true, true, Map.of()
        );

        ResourcePlanResult result = planner.planCraftAndDeliver(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1,
                "owner", observation);

        require(!result.completed(), "resource plan must not fake delivery completion");
        require(result.root().children().size() == 2, "root must include craft and delivery children");
        require(result.blockers().stream().anyMatch(reason -> reason.contains("missing pickaxe")),
                "furnace plan must expose missing pickaxe prerequisite for cobblestone");
        require(result.blockers().stream().anyMatch(reason -> reason.contains("missing crafting table workstation")),
                "furnace plan must expose workstation requirement");
        require(result.blockers().stream().anyMatch(reason -> reason.contains("delivery item not yet")),
                "furnace plan must keep delivery blocked until inventory is real");
        require(result.nextReadyLeaf().isEmpty(), "blocked prerequisites must prevent a fake ready leaf");
    }

    private static void furnacePlanUpdatesNextLeafAfterMaterialsArrive() {
        ResourceDependencyPlanner planner = new ResourceDependencyPlanner();
        ResourcePlanObservation needsLocate = observation(
                Map.of(OpenPlayerConstants.MINECRAFT_WOODEN_PICKAXE_ID, 1,
                        OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID, 1),
                Set.of(), true, true, true, true, Map.of("collect_block:minecraft:cobblestone", 2)
        );

        ResourcePlanResult locate = planner.planCraftAndDeliver(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1,
                "owner", needsLocate);

        require(locate.nextReadyLeaf().orElseThrow().kind() == ResourcePlanKind.COLLECT_BLOCK,
                "missing cobblestone must produce a collect-block leaf");
        require("find_loaded_blocks".equals(locate.nextReadyLeaf().orElseThrow().primitive()),
                "without observed stone the next primitive must locate loaded blocks");
        require(locate.nextReadyLeaf().orElseThrow().failCount() == 2,
                "plan must preserve repeated observation fail counts");

        ResourcePlanObservation canCraft = observation(
                Map.of(OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID, 8,
                        OpenPlayerConstants.MINECRAFT_WOODEN_PICKAXE_ID, 1,
                        OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID, 1),
                Set.of(OpenPlayerConstants.MINECRAFT_STONE_ID), true, true, true, true, Map.of()
        );
        ResourcePlanResult craft = planner.planCraftAndDeliver(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1,
                "owner", canCraft);

        require(craft.nextReadyLeaf().orElseThrow().kind() == ResourcePlanKind.CRAFT,
                "after inputs arrive the next ready leaf must be craft");
        require("craft".equals(craft.nextReadyLeaf().orElseThrow().primitive()),
                "crafting must remain a primitive, not a hidden furnace macro");
    }

    private static void furnacePlanMovesToDeliveryAfterCraftedItemExists() {
        ResourceDependencyPlanner planner = new ResourceDependencyPlanner();
        ResourcePlanObservation observation = observation(
                Map.of(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1), Set.of(), true, true, true, true, Map.of()
        );

        ResourcePlanResult result = planner.planCraftAndDeliver(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1,
                "owner", observation);

        require(result.nextReadyLeaf().orElseThrow().kind() == ResourcePlanKind.DELIVERY,
                "crafted target item must advance the next ready leaf to delivery, not repeat craft");
        require("drop_item".equals(result.nextReadyLeaf().orElseThrow().primitive()),
                "delivery leaf must use the drop/delivery primitive after the item exists");
    }

    private static void craftingValidatorReportsRecipeWorkstationAndInputState() {
        ResourceRecipe recipe = ResourceDependencyPlanner.builtinRecipes().get(OpenPlayerConstants.MINECRAFT_FURNACE_ID);
        ObjectiveProgress missingRecipe = CraftingObjectiveValidator.validate(null, Map.of(), true, true, 1);
        require(!missingRecipe.supported(), "missing recipe must be unsupported, not success");

        ObjectiveProgress missingInputs = CraftingObjectiveValidator.validate(recipe,
                Map.of(OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID, 7), true, false, 1);
        require(!missingInputs.completed(), "crafting must not complete with missing input or workstation");
        require(missingInputs.missingItems().get(OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID) == 1,
                "crafting missing material count must be precise");
        require(missingInputs.missingItems().containsKey(OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID),
                "crafting validator must report workstation requirement");

        ObjectiveProgress ready = CraftingObjectiveValidator.validate(recipe,
                Map.of(OpenPlayerConstants.MINECRAFT_COBBLESTONE_ID, 8), true, true, 1);
        require(ready.completed(), "crafting validator must complete only when recipe, inputs, and workstation are verified");
    }

    private static void smeltingValidatorReportsMissingAdapterFuelInputOutputAndProgress() {
        ObjectiveProgress missingAdapter = SmeltingObjectiveValidator.validate(null);
        require(!missingAdapter.supported(), "missing smelting status must report missing adapter");

        ObjectiveProgress blocked = SmeltingObjectiveValidator.validate(new SmeltingObjectiveValidator.SmeltingStatus(
                true, false, false, "minecraft:raw_iron", 1, false, false, true, 0, 1
        ));
        require(!blocked.completed(), "blocked smelting must not complete");
        require(blocked.missingItems().get("minecraft:raw_iron") == 1, "smelting must report missing input item");
        require(blocked.blockerReasons().stream().anyMatch(reason -> reason.contains("fuel missing")),
                "smelting must report fuel blocker");
        require(blocked.blockerReasons().stream().anyMatch(reason -> reason.contains("output slot blocked")),
                "smelting must report output slot blocker");
    }

    private static void deliveryValidatorRequiresObservedInventoryOrVisibleCollectableDrop() {
        ObjectiveProgress fake = DeliveryObjectiveValidator.validate(Map.of(), Map.of(),
                Map.of(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1), null);
        require(!fake.completed(), "delivery must not complete without observed target inventory delta or drop evidence");

        ObjectiveProgress dropped = DeliveryObjectiveValidator.validate(Map.of(), Map.of(),
                Map.of(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1),
                new DeliveryObjectiveValidator.DeliveryDropEvidence(true, true, 1.5D, true, true, true,
                        Map.of(OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1)));
        require(dropped.completed(), "visible collectable drop near target must verify intended drop delivery");
    }

    private static void scopedToolDocsForegroundFurnaceCapabilitiesAndSuppressUnrelatedTools() {
        String docs = CapabilityScopedToolDocs.forObjective("make a furnace and throw it to me");
        require(docs.contains("find_loaded_blocks"), "furnace docs must include loaded block search");
        require(docs.contains("craft"), "furnace docs must include crafting");
        require(docs.contains("drop_item") || docs.contains("toss"), "furnace docs must include drop or toss delivery");
        require(!docs.contains("creative_set_inventory_slot"), "scoped docs must suppress rejected creative tools");
        require(!docs.contains("pvp_attack"), "scoped docs must suppress unrelated combat tools");
    }

    private static void modelRolesAreRedactedAndCannotBypassValidators() {
        ModelRoleConfig roles = new ModelRoleConfig("chat-small", "planner-large", "tools-fast",
                Map.of("api_key", "secret", "planning_token", "secret2"));
        String status = roles.status().toString();
        require(status.contains("chat-small"), "role status must expose selected chat role");
        require(status.contains("configured_redacted"), "credential status must be redacted");
        require(!status.contains("secret"), "credential values must not appear in status");
        require(status.contains("validators_bypassed=false"), "role separation must not bypass validators");
    }

    private static ResourcePlanObservation observation(Map<String, Integer> inventory, Set<String> visibleBlocks,
                                                       boolean collectBlockAdapter, boolean craftingAdapter,
                                                       boolean deliveryAdapter, boolean worldAllowed,
                                                       Map<String, Integer> failCounts) {
        return new ResourcePlanObservation(inventory, visibleBlocks, ResourceDependencyPlanner.builtinRecipes(), failCounts,
                collectBlockAdapter, craftingAdapter, deliveryAdapter, worldAllowed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
