package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.aicore.AICoreNpcToolExecutor;
import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.aicore.ToolCall;
import dev.soffits.openplayer.aicore.ToolResult;
import dev.soffits.openplayer.aicore.ToolResultStatus;
import dev.soffits.openplayer.aicore.ToolValidationContext;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.CollectItemsInstructionParser.CollectItemsInstruction;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskPolicy;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.List;
import java.util.Queue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

final class VanillaAutomationController extends VanillaAutomationRuntimeController {
    VanillaAutomationController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

@Override
public void setOwnerId(NpcOwnerId ownerId) {
    if (ownerId == null) {
        throw new IllegalArgumentException("ownerId cannot be null");
    }
    this.ownerId = ownerId;
}

@Override
public AutomationCommandResult submit(CommandIntent intent) {
    if (intent == null) {
        throw new IllegalArgumentException("intent cannot be null");
    }
    ToolCall toolCall = MinecraftPrimitiveTools.toToolCall(intent).orElse(null);
    if (toolCall == null) {
        return submitPrimitiveIntent(intent);
    }
    AICoreNpcToolExecutor executor = new AICoreNpcToolExecutor(entity, aicoreEventBus,
            primitiveIntent -> {
                AutomationCommandResult primitiveResult = submitPrimitiveIntent(primitiveIntent);
                CommandSubmissionStatus status = primitiveResult.status() == AutomationCommandStatus.ACCEPTED
                        ? CommandSubmissionStatus.ACCEPTED
                        : CommandSubmissionStatus.REJECTED;
                return new CommandSubmissionResult(status, primitiveResult.message());
            });
    ToolResult result = executor.execute(toolCall, new ToolValidationContext(entity.allowWorldActions()));
    if (result.status() == ToolResultStatus.SUCCESS || result.status() == ToolResultStatus.RUNNING) {
        return accepted(result.summary());
    }
    return rejected(result.reason());
}

protected AutomationCommandResult submitPrimitiveIntent(CommandIntent intent) {
    IntentKind kind = intent.kind();
    if (kind == IntentKind.PROVIDER_PLAN) {
        return submitProviderPlan(intent);
    }
    if (kind == IntentKind.STOP) {
        stopAll();
        return accepted("STOP accepted");
    }
    if (kind == IntentKind.PAUSE) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("PAUSE requires a blank instruction");
        }
        paused = true;
        suspendNavigation();
        entity.setDeltaMovement(Vec3.ZERO);
        return accepted("PAUSE accepted");
    }
    if (kind == IntentKind.UNPAUSE) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("UNPAUSE requires a blank instruction");
        }
        paused = false;
        reissueActiveNavigation();
        return accepted("UNPAUSE accepted");
    }
    if (kind == IntentKind.RESET_MEMORY) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("RESET_MEMORY requires a blank instruction");
        }
        return accepted("RESET_MEMORY accepted: no automation-local memory was cleared");
    }
    if (kind == IntentKind.REPORT_STATUS) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("REPORT_STATUS requires a blank instruction");
        }
        return accepted("REPORT_STATUS accepted: " + statusSummary());
    }
    if (kind == IntentKind.BODY_LANGUAGE) {
        BodyLanguageInstruction bodyLanguage = BodyLanguageInstructionParser.parseOrNull(intent.instruction());
        if (bodyLanguage == null) {
            return rejected(BodyLanguageInstructionParser.USAGE);
        }
        if (paused) {
            return rejected("BODY_LANGUAGE is unavailable while automation is paused");
        }
        return applyBodyLanguage(bodyLanguage);
    }
    if (kind == IntentKind.INVENTORY_QUERY) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("INVENTORY_QUERY requires a blank instruction");
        }
        return accepted("INVENTORY_QUERY accepted: " + entity.inventorySummary());
    }
    if (kind == IntentKind.MOVE) {
        Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
        if (coordinate == null) {
            return rejected("MOVE requires instruction: x y z");
        }
        if (!isCoordinateLoaded(coordinate)) {
            return rejected("MOVE target chunk is not loaded");
        }
        queuedCommands.add(QueuedCommand.move(coordinate));
        return accepted("MOVE accepted");
    }
    if (kind == IntentKind.GOTO) {
        return submitGoto(intent.instruction());
    }
    if (kind == IntentKind.LOOK) {
        Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
        if (coordinate == null) {
            return rejected("LOOK requires instruction: x y z");
        }
        queuedCommands.add(QueuedCommand.look(coordinate));
        return accepted("LOOK accepted");
    }
    if (kind == IntentKind.FOLLOW_OWNER) {
        if (ownerId == null) {
            return rejected("FOLLOW_OWNER requires an NPC owner");
        }
        if (owner() == null) {
            return rejected("NPC owner is unavailable in this dimension");
        }
        queuedCommands.add(QueuedCommand.followOwner());
        return accepted("FOLLOW_OWNER accepted");
    }
    if (kind == IntentKind.PATROL) {
        Coordinate coordinate = AutomationInstructionParser.parseBoundedCoordinateOrNull(
                intent.instruction(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                PATROL_MAX_DISTANCE
        );
        if (coordinate == null) {
            return rejected("PATROL requires instruction x y z within " + (int) PATROL_MAX_DISTANCE + " blocks");
        }
        if (!isCoordinateLoaded(coordinate)) {
            return rejected("PATROL target chunk is not loaded");
        }
        queuedCommands.add(QueuedCommand.patrol(coordinate));
        return accepted("PATROL accepted");
    }
    if (isLocalWorldOrInventoryAction(kind) && !entity.allowWorldActions()) {
        return rejected("World actions are disabled for this OpenPlayer character");
    }
    if (kind == IntentKind.COLLECT_ITEMS) {
        CollectItemsInstruction collectInstruction = CollectItemsInstructionParser.parseOrNull(intent.instruction());
        if (collectInstruction == null) {
            return rejected(CollectItemsInstructionParser.USAGE);
        }
        queuedCommands.add(QueuedCommand.collectItems(collectInstruction.item(), collectInstruction.radius()));
        return accepted("COLLECT_ITEMS accepted");
    }
    if (kind == IntentKind.SWAP_TO_OFFHAND) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return rejected("SWAP_TO_OFFHAND requires a blank instruction");
        }
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        if (!acquireInteractionCooldown() || !entity.swapSelectedHotbarStackToOffhand()) {
            rollbackInteractionCooldown();
            return rejected("SWAP_TO_OFFHAND requires a selected hotbar stack");
        }
        entity.swingMainHandAction();
        return accepted("SWAP_TO_OFFHAND accepted");
    }
    if (kind == IntentKind.DROP_ITEM) {
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        boolean dropped;
        String failureMessage;
        if (AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            dropped = acquireInteractionCooldown() && entity.dropSelectedHotbarStack();
            failureMessage = "DROP_ITEM requires a selected hotbar stack";
        } else {
            ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(
                    intent.instruction(), false
            );
            if (parsed == null) {
                return rejected("DROP_ITEM requires blank or instruction: <item_id> [count]");
            }
            dropped = acquireInteractionCooldown() && entity.dropInventoryItem(parsed.item(), parsed.count());
            failureMessage = "DROP_ITEM requires the requested item count in NPC inventory";
        }
        if (!dropped) {
            rollbackInteractionCooldown();
            return rejected(failureMessage);
        }
        entity.swingMainHandAction();
        return accepted("DROP_ITEM accepted");
    }
    if (kind == IntentKind.EQUIP_ITEM) {
        ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemOnlyOrNull(intent.instruction());
        if (parsed == null) {
            return rejected("EQUIP_ITEM requires instruction: <item_id>");
        }
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        if (!acquireInteractionCooldown() || !entity.equipMatchingItem(parsed.item())) {
            rollbackInteractionCooldown();
            return rejected("EQUIP_ITEM found no matching equippable armor or hotbar item: " + parsed.itemId());
        }
        entity.swingMainHandAction();
        return accepted("EQUIP_ITEM accepted");
    }
    if (kind == IntentKind.GIVE_ITEM) {
        ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), true);
        if (parsed == null) {
            return rejected("GIVE_ITEM requires instruction: <item_id> [count] [owner]");
        }
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive()) {
            return rejected("GIVE_ITEM requires the NPC owner online and alive in this dimension");
        }
        if (entity.distanceToSqr(owner) > OWNER_ITEM_TRANSFER_DISTANCE * OWNER_ITEM_TRANSFER_DISTANCE) {
            return rejected("GIVE_ITEM requires the NPC owner within " + (int) OWNER_ITEM_TRANSFER_DISTANCE + " blocks");
        }
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        if (!acquireInteractionCooldown() || !entity.giveInventoryItemToPlayer(owner, parsed.item(), parsed.count())) {
            rollbackInteractionCooldown();
            return rejected("GIVE_ITEM requires the requested item count and owner inventory capacity");
        }
        entity.swingMainHandAction();
        return accepted("GIVE_ITEM accepted");
    }
    if (kind == IntentKind.DEPOSIT_ITEM || kind == IntentKind.STASH_ITEM) {
        WorkRepeatPolicy.InventoryRepeatInstruction repeatInstruction = WorkRepeatPolicy
                .parseInventoryRepeatInstructionOrNull(intent.instruction());
        if (repeatInstruction == null) {
            return rejected(kind.name() + " requires blank or instruction: <item_id> [count] [repeat=1.."
                    + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
        }
        ParsedItemInstruction parsed = null;
        if (!AutomationInstructionParser.isBlankInstruction(repeatInstruction.itemInstruction())) {
            parsed = InventoryActionInstructionParser.parseItemCountOrNull(
                    repeatInstruction.itemInstruction(), false
            );
            if (parsed == null) {
                return rejected(kind.name() + " requires blank or instruction: <item_id> [count] [repeat=1.."
                        + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
            }
        }
        SafeContainerTarget target = kind == IntentKind.STASH_ITEM ? preferredStashContainer() : nearestSafeContainer();
        if (target == null) {
            return rejected(kind.name() + " requires a loaded nearby safe Container block entity adapter");
        }
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        if (!acquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        int completedRepeats = 0;
        for (int repeatIndex = 0; repeatIndex < repeatInstruction.repeatCount(); repeatIndex++) {
            if (parsed == null && !hasNormalInventoryToTransfer()) {
                if (completedRepeats == 0) {
                    rollbackInteractionCooldown();
                    return rejected(kind.name() + " repeat stopped after " + completedRepeats
                            + " completed iteration(s): no_inventory_to_transfer");
                }
                if (kind == IntentKind.STASH_ITEM) {
                    entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                }
                target.container().setChanged();
                entity.swingMainHandAction();
                return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                        + " repeat=" + completedRepeats + "/" + repeatInstruction.repeatCount()
                        + " stopped=no_inventory_to_transfer");
            }
            boolean transferred = depositToContainer(target.container(), parsed);
            if (!transferred) {
                if (completedRepeats == 0) {
                    rollbackInteractionCooldown();
                }
                if (completedRepeats > 0) {
                    if (kind == IntentKind.STASH_ITEM) {
                        entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                    }
                    target.container().setChanged();
                    entity.swingMainHandAction();
                    return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                            + " repeat=" + completedRepeats + "/" + repeatInstruction.repeatCount()
                            + " stopped=no_more_safe_transfer");
                }
                return rejected(kind.name() + " repeat stopped after " + completedRepeats
                        + " completed iteration(s): all requested normal inventory items must fit in the container");
            }
            completedRepeats++;
        }
        if (kind == IntentKind.STASH_ITEM) {
            entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
        }
        target.container().setChanged();
        entity.swingMainHandAction();
        return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                + " repeat=" + completedRepeats);
    }
    if (kind == IntentKind.WITHDRAW_ITEM) {
        ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
        if (parsed == null) {
            return rejected("WITHDRAW_ITEM requires instruction: <item_id> [count]");
        }
        SafeContainerTarget target = preferredStashContainer();
        if (target == null) {
            return rejected("WITHDRAW_ITEM requires a loaded nearby safe Container block entity adapter");
        }
        if (!canAcquireInteractionCooldown()) {
            return rejected(interactionCooldownMessage());
        }
        boolean transferred = acquireInteractionCooldown()
                && withdrawFromContainer(target.container(), parsed.item(), parsed.count());
        if (!transferred) {
            rollbackInteractionCooldown();
            return rejected("WITHDRAW_ITEM requires exact item count in the container and NPC inventory capacity");
        }
        target.container().setChanged();
        entity.swingMainHandAction();
        return accepted("WITHDRAW_ITEM accepted: " + parsed.itemId() + " x" + parsed.count()
                + " from container " + target.blockPos().toShortString());
    }
    if (kind == IntentKind.BREAK_BLOCK) {
        Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
        if (coordinate == null) {
            return rejected("BREAK_BLOCK requires instruction: x y z");
        }
        if (!isCoordinateLoaded(coordinate)) {
            return rejected("BREAK_BLOCK target chunk is not loaded");
        }
        queuedCommands.add(QueuedCommand.breakBlock(coordinate));
        return accepted("BREAK_BLOCK accepted");
    }
    if (kind == IntentKind.PLACE_BLOCK) {
        Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
        if (coordinate == null) {
            return rejected("PLACE_BLOCK requires instruction: x y z");
        }
        if (!isCoordinateLoaded(coordinate)) {
            return rejected("PLACE_BLOCK target chunk is not loaded");
        }
        if (!(entity.getMainHandItem().getItem() instanceof BlockItem)
                && !entity.selectFirstHotbarBlockItem()) {
            return rejected("PLACE_BLOCK requires a block item in the NPC selected hotbar slot");
        }
        queuedCommands.add(QueuedCommand.placeBlock(coordinate));
        return accepted("PLACE_BLOCK accepted");
    }
    if (kind == IntentKind.INTERACT) {
        return submitInteract(intent.instruction());
    }
    if (kind == IntentKind.ATTACK_NEAREST) {
        double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                intent.instruction(), ATTACK_DEFAULT_RADIUS, ATTACK_MAX_RADIUS
        );
        if (radius < 0.0D) {
            return rejected("ATTACK_NEAREST instruction must be blank or a positive radius number");
        }
        queuedCommands.add(QueuedCommand.attackNearest(radius));
        return accepted("ATTACK_NEAREST accepted");
    }
    if (kind == IntentKind.ATTACK_TARGET) {
        return submitAttackTarget(intent.instruction());
    }
    if (kind == IntentKind.GUARD_OWNER) {
        if (ownerId == null) {
            return rejected("GUARD_OWNER requires an NPC owner");
        }
        if (owner() == null) {
            return rejected("NPC owner is unavailable in this dimension");
        }
        double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                intent.instruction(), GUARD_DEFAULT_RADIUS, GUARD_MAX_RADIUS
        );
        if (radius < 0.0D) {
            return rejected("GUARD_OWNER instruction must be blank or a positive radius number");
        }
        queuedCommands.add(QueuedCommand.guardOwner(radius));
        return accepted("GUARD_OWNER accepted");
    }
    if (kind == IntentKind.LOCATE_LOADED_BLOCK) {
        return reportLoadedBlock(intent.instruction());
    }
    if (kind == IntentKind.LOCATE_LOADED_ENTITY) {
        return reportLoadedEntity(intent.instruction());
    }
    if (kind == IntentKind.FIND_LOADED_BIOME) {
        return reportLoadedBiome(intent.instruction());
    }
    if (AdvancedTaskPolicy.isUnsupportedAdvancedKind(kind)) {
        return rejected(AdvancedTaskPolicy.unsupportedReason(kind));
    }
    return rejected("Unsupported intent: " + kind.name());
}

protected AutomationCommandResult submitProviderPlan(CommandIntent intent) {
    List<CommandIntent> steps;
    try {
        steps = ProviderPlanIntentCodec.decode(intent.instruction());
    } catch (IllegalArgumentException exception) {
        return rejected(exception.getMessage());
    }
    for (CommandIntent step : steps) {
        RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(step, entity.allowWorldActions());
        if (!validation.isAccepted()) {
            return rejected("PROVIDER_PLAN step rejected: " + validation.message());
        }
    }
    if (steps.size() > 1) {
        return rejected("PROVIDER_PLAN with multiple steps requires the interactive planner");
    }
    for (CommandIntent step : steps) {
        AutomationCommandResult result = submitPrimitiveIntent(step);
        if (result.status() != AutomationCommandStatus.ACCEPTED) {
            return rejected("PROVIDER_PLAN step rejected: " + result.message());
        }
    }
    return accepted("PROVIDER_PLAN accepted: " + steps.size()
            + " validated primitive step(s) accepted or queued without replanning");
}
}
