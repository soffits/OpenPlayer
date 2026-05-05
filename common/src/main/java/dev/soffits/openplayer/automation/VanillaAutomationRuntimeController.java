package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.aicore.AICoreEventBus;
import dev.soffits.openplayer.aicore.AICoreNpcToolExecutor;
import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.aicore.ToolCall;
import dev.soffits.openplayer.aicore.ToolResult;
import dev.soffits.openplayer.aicore.ToolResultStatus;
import dev.soffits.openplayer.aicore.ToolValidationContext;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.CollectItemsInstructionParser.CollectItemsInstruction;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import dev.soffits.openplayer.automation.InteractionInstruction.InteractionTargetKind;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.LoadedSearchInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskPolicy;
import dev.soffits.openplayer.automation.capability.RuntimeCapabilityRegistry;
import dev.soffits.openplayer.automation.navigation.LoadedAreaNavigator;
import dev.soffits.openplayer.automation.navigation.NavigationRuntime;
import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.navigation.NavigationTargetKind;
import dev.soffits.openplayer.automation.policy.MovementPolicyLoader;
import dev.soffits.openplayer.automation.policy.MovementProfile;
import dev.soffits.openplayer.automation.survival.SurvivalCooldownPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalDangerKind;
import dev.soffits.openplayer.automation.survival.SurvivalDangerPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalHealthPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalTargetPolicy;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

abstract class VanillaAutomationRuntimeController extends VanillaAutomationWorkController {
    protected VanillaAutomationRuntimeController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected AutomationCommandResult submitGoto(String instruction) {
    Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(instruction);
    if (coordinate == null) {
        return rejected("GOTO requires instruction: x y z");
    }
    return submitGotoCoordinate(coordinate);
}

protected AutomationCommandResult submitGotoCoordinate(Coordinate coordinate) {
    if (!isCoordinateLoaded(coordinate)) {
        return rejected("GOTO target chunk is not loaded");
    }
    queuedCommands.add(QueuedCommand.gotoCoordinate(coordinate));
    return accepted("GOTO accepted: position");
}

protected AutomationCommandResult reportLoadedBlock(String instruction) {
    LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
    if (loadedSearchInstruction == null) {
        return rejected(AdvancedTaskInstructionParser.LOCATE_LOADED_BLOCK_USAGE);
    }
    LoadedAreaNavigator.BlockSearchResult result = loadedAreaNavigator.nearestLoadedBlock(
            serverLevel(), entity.position(), loadedSearchInstruction.resourceId(), loadedSearchInstruction.radius()
    );
    if (!result.found()) {
        return accepted("LOCATE_LOADED_BLOCK not found: target=" + loadedSearchInstruction.resourceId()
                + " diagnostics=" + result.diagnostics().summary());
    }
    return accepted("LOCATE_LOADED_BLOCK found: target=" + loadedSearchInstruction.resourceId()
            + " pos=" + result.blockPos().toShortString()
            + " recommended_next_action=break_block_at recommended_next_pos=" + result.blockPos().toShortString()
            + " diagnostics=" + result.diagnostics().summary());
}

protected AutomationCommandResult reportLoadedEntity(String instruction) {
    LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
    if (loadedSearchInstruction == null) {
        return rejected(AdvancedTaskInstructionParser.LOCATE_LOADED_ENTITY_USAGE);
    }
    LoadedAreaNavigator.EntitySearchResult result = loadedAreaNavigator.nearestLoadedEntity(
            serverLevel(),
            entity.position(),
            loadedSearchInstruction.resourceId(),
            loadedSearchInstruction.radius(),
            candidate -> candidate != entity && candidate.isAlive()
    );
    if (!result.found()) {
        return accepted("LOCATE_LOADED_ENTITY not found: target=" + loadedSearchInstruction.resourceId()
                + " diagnostics=" + result.diagnostics().summary());
    }
    return accepted("LOCATE_LOADED_ENTITY found: target=" + loadedSearchInstruction.resourceId()
            + " pos=" + result.entity().blockPosition().toShortString()
            + " diagnostics=" + result.diagnostics().summary());
}

protected AutomationCommandResult reportLoadedBiome(String instruction) {
    LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
    if (loadedSearchInstruction == null) {
        return rejected(AdvancedTaskInstructionParser.FIND_LOADED_BIOME_USAGE);
    }
    LoadedAreaNavigator.BiomeSearchResult result = loadedAreaNavigator.nearestLoadedBiome(
            serverLevel(), entity.position(), loadedSearchInstruction.resourceId(), loadedSearchInstruction.radius()
    );
    if (!result.found()) {
        return accepted("FIND_LOADED_BIOME not found: target=" + loadedSearchInstruction.resourceId()
                + " diagnostics=" + result.diagnostics().summary());
    }
    return accepted("FIND_LOADED_BIOME found: target=" + loadedSearchInstruction.resourceId()
            + " pos=" + result.blockPos().toShortString()
            + " diagnostics=" + result.diagnostics().summary());
}

@Override
    public void tick() {
    if (entity.level().isClientSide) {
        return;
    }
    if (paused) {
        entity.setDeltaMovement(Vec3.ZERO);
        return;
    }
    interactionCooldown.tick();
    survivalCooldown.tick();
    if (activeCommand == null) {
        activeCommand = queuedCommands.poll();
        if (activeCommand == null) {
            monitorIdleSurvival();
            return;
        }
        activeMonitor = newMonitor(activeCommand);
        activeMonitor.start(entity.getX(), entity.getY(), entity.getZ());
        if (activeCommand.repeatRemaining() > 1) {
            activeMonitor.note("repeat:remaining=" + activeCommand.repeatRemaining());
        }
        OpenPlayerDebugEvents.record("automation", "started", null, null, null,
                "kind=" + activeCommand.kind().name());
        OpenPlayerRawTrace.automationOperation("started", activeCommand.kind().name(),
                "entity=" + entity.getUUID() + " position=" + entity.position()
                        + " coordinate=" + activeCommand.coordinate());
        start(activeCommand);
    }
    continueActiveCommand();
    watchdogActiveCommand();
}

protected void start(QueuedCommand command) {
    if (command.kind() == IntentKind.MOVE) {
        Coordinate coordinate = command.coordinate();
        moveToPosition(coordinate);
        return;
    }
    if (command.kind() == IntentKind.GOTO) {
        startGoto(command);
        return;
    }
    if (command.kind() == IntentKind.LOOK) {
        Coordinate coordinate = command.coordinate();
        entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
        completeActiveCommand();
        return;
    }
    if (command.kind() == IntentKind.COLLECT_ITEMS
            || command.kind() == IntentKind.BREAK_BLOCK
            || command.kind() == IntentKind.PLACE_BLOCK
            || command.kind() == IntentKind.INTERACT
            || command.kind() == IntentKind.ATTACK_NEAREST
            || command.kind() == IntentKind.ATTACK_TARGET
            || command.kind() == IntentKind.GUARD_OWNER
            || command.kind() == IntentKind.PATROL) {
        command.setStartPosition(entity.blockPosition());
    }
    if (command.kind() == IntentKind.PATROL) {
        Coordinate coordinate = command.coordinate();
        moveToPosition(coordinate);
    }
}

protected void continueActiveCommand() {
    if (activeCommand == null) {
        return;
    }
    if (activeCommand.kind() == IntentKind.MOVE) {
        navigationRuntime.updateDistance(distanceTo(activeCommand.coordinate()));
        if (entity.getNavigation().isDone()) {
            completeActiveCommand();
        }
        return;
    }
    if (activeCommand.kind() == IntentKind.GOTO) {
        continueGoto(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.FOLLOW_OWNER) {
        followOwner();
        return;
    }
    if (activeCommand.kind() == IntentKind.COLLECT_ITEMS) {
        collectItems(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.BREAK_BLOCK) {
        breakBlock(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.PLACE_BLOCK) {
        placeBlock(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.INTERACT) {
        if (activeCommand.entityTarget() != null) {
            interactEntity(activeCommand);
        } else {
            interactBlock(activeCommand);
        }
        return;
    }
    if (activeCommand.kind() == IntentKind.ATTACK_NEAREST) {
        attackNearest(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.ATTACK_TARGET) {
        attackSpecificTarget(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.GUARD_OWNER) {
        guardOwner(activeCommand);
        return;
    }
    if (activeCommand.kind() == IntentKind.PATROL) {
        patrol(activeCommand);
    }
}
}
