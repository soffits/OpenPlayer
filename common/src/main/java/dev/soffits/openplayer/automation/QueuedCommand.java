package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

        final class QueuedCommand {
private final IntentKind kind;
private final Coordinate coordinate;
private final double radius;
private final BlockPos blockTarget;
private final Entity entityTarget;
private final Item collectItem;
private final int maxTicks;
private final boolean survivalOnly;
private final int repeatRemaining;
BlockPos startPosition;
int reachTicks;
boolean patrolReturn;
private UUID itemTargetId;
private Vec3 lastItemTargetPosition;
int startInventoryCount;

private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius) {
    this(kind, coordinate, radius, null, null, null, 0, false, 1);
}

private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius, BlockPos blockTarget,
                        Entity entityTarget, Item collectItem, int maxTicks, boolean survivalOnly,
                        int repeatRemaining) {
    this.kind = kind;
    this.coordinate = coordinate;
    this.radius = radius;
    this.blockTarget = blockTarget;
    this.entityTarget = entityTarget;
    this.collectItem = collectItem;
    this.maxTicks = maxTicks;
    this.survivalOnly = survivalOnly;
    this.repeatRemaining = repeatRemaining;
}

static QueuedCommand move(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.MOVE, coordinate, 0.0D);
}

static QueuedCommand gotoCoordinate(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.GOTO, coordinate, 0.0D);
}

static QueuedCommand look(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.LOOK, coordinate, 0.0D);
}

static QueuedCommand followOwner() {
    return new QueuedCommand(IntentKind.FOLLOW_OWNER, null, 0.0D);
}

static QueuedCommand collectItems(Item collectItem, double radius) {
    return new QueuedCommand(IntentKind.COLLECT_ITEMS, null, radius, null, null, collectItem, 0, false, 1);
}

static QueuedCommand breakBlock(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.BREAK_BLOCK, coordinate, 0.0D);
}

static QueuedCommand placeBlock(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.PLACE_BLOCK, coordinate, 0.0D);
}

static QueuedCommand interactBlock(BlockPos blockPos) {
    return new QueuedCommand(
            IntentKind.INTERACT, null, 0.0D, blockPos.immutable(), null, null, 0, false, 1
    );
}

static QueuedCommand interactEntity(Entity target, double radius) {
    return new QueuedCommand(
            IntentKind.INTERACT, null, radius, null, target, null, 0, false, 1
    );
}

static QueuedCommand attackNearest(double radius) {
    return new QueuedCommand(IntentKind.ATTACK_NEAREST, null, radius);
}

static QueuedCommand attackTarget(Entity target, double radius) {
    return new QueuedCommand(IntentKind.ATTACK_TARGET, null, radius, null, target, null, 0, false, 1);
}

static QueuedCommand selfDefense(double radius) {
    return new QueuedCommand(
            IntentKind.ATTACK_NEAREST, null, radius, null, null, null, 0, true, 1
    );
}

static QueuedCommand guardOwner(double radius) {
    return new QueuedCommand(IntentKind.GUARD_OWNER, null, radius);
}

static QueuedCommand patrol(Coordinate coordinate) {
    return new QueuedCommand(IntentKind.PATROL, coordinate, 0.0D);
}

QueuedCommand nextRepeat() {
    return new QueuedCommand(kind, coordinate, radius, blockTarget, entityTarget, collectItem, maxTicks, survivalOnly,
            repeatRemaining - 1);
}

IntentKind kind() {
    return kind;
}

Coordinate coordinate() {
    return coordinate;
}

double radius() {
    return radius;
}

BlockPos blockTarget() {
    return blockTarget;
}

Entity entityTarget() {
    return entityTarget;
}

Item collectItem() {
    return collectItem;
}

int maxTicks() {
    return maxTicks;
}

boolean survivalOnly() {
    return survivalOnly;
}

int repeatRemaining() {
    return repeatRemaining;
}

int reachTicks() {
    return reachTicks;
}

void incrementReachTicks() {
    reachTicks++;
}

void resetReachTicks() {
    reachTicks = 0;
}

void trackItemTarget(ItemEntity itemEntity, int inventoryCount) {
    if (itemTargetId == null || !itemTargetId.equals(itemEntity.getUUID())) {
        itemTargetId = itemEntity.getUUID();
        startInventoryCount = inventoryCount;
    }
    lastItemTargetPosition = itemEntity.position();
}

boolean trackedItemTarget() {
    return itemTargetId != null;
}

int startInventoryCount() {
    return startInventoryCount;
}

boolean lastTargetClose(Vec3 position) {
    return lastItemTargetPosition != null
            && position.distanceToSqr(lastItemTargetPosition)
            <= (1.5D + 0.75D) * (1.5D + 0.75D);
}

BlockPos startPosition() {
    return startPosition;
}

void setStartPosition(BlockPos startPosition) {
    this.startPosition = startPosition;
}

boolean returningToStart() {
    return patrolReturn;
}

void togglePatrolReturn() {
    patrolReturn = !patrolReturn;
}

BlockPos blockPos() {
    return BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z());
}
        }
