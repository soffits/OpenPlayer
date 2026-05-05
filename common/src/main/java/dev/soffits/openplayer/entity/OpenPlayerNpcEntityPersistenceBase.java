package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationControllerSnapshot;
import dev.soffits.openplayer.automation.VanillaAutomationBackend;
import dev.soffits.openplayer.aicore.AICoreNpcSessionState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


abstract class OpenPlayerNpcEntityPersistenceBase extends OpenPlayerNpcEntityBase {
    protected OpenPlayerNpcEntityPersistenceBase(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void swingMainHandAction() {
        swing(InteractionHand.MAIN_HAND);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, VanillaAutomationBackend.PLAYER_LIKE_MOVEMENT_ATTRIBUTE_SPEED)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return List.of(getItemBySlot(EquipmentSlot.MAINHAND), getItemBySlot(EquipmentSlot.OFFHAND));
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of(
                getItemBySlot(EquipmentSlot.FEET),
                getItemBySlot(EquipmentSlot.LEGS),
                getItemBySlot(EquipmentSlot.CHEST),
                getItemBySlot(EquipmentSlot.HEAD)
        );
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot equipmentSlot) {
        int inventorySlot = inventorySlotForEquipmentSlot(equipmentSlot);
        if (inventorySlot >= 0) {
            return internalInventory.get(inventorySlot);
        }
        return super.getItemBySlot(equipmentSlot);
    }

    @Override
    public void setItemSlot(EquipmentSlot equipmentSlot, ItemStack itemStack) {
        int inventorySlot = inventorySlotForEquipmentSlot(equipmentSlot);
        if (inventorySlot >= 0) {
            internalInventory.set(inventorySlot, itemStack.copy());
            return;
        }
        super.setItemSlot(equipmentSlot, itemStack);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        ListTag inventoryTag = new ListTag();
        for (int slot = 0; slot < internalInventory.size(); slot++) {
            ItemStack itemStack = internalInventory.get(slot);
            if (!itemStack.isEmpty()) {
                CompoundTag itemTag = itemStack.save(new CompoundTag());
                itemTag.putByte(INVENTORY_SLOT_TAG, (byte) slot);
                inventoryTag.add(itemTag);
            }
        }
        compoundTag.put(INVENTORY_TAG, inventoryTag);
        compoundTag.putInt(SELECTED_MAIN_HAND_SLOT_TAG, selectedMainHandSlot);
        if (hasValidPersistedIdentity()) {
            compoundTag.putUUID(OWNER_ID_TAG, persistedOwnerId);
            compoundTag.putString(ROLE_ID_TAG, persistedRoleId);
            compoundTag.putString(PROFILE_NAME_TAG, persistedProfileName);
            if (persistedProfileSkinTexture != null) {
                compoundTag.putString(PROFILE_SKIN_TEXTURE_TAG, persistedProfileSkinTexture);
            }
            if (persistedMovementPolicy != null) {
                compoundTag.putString(MOVEMENT_POLICY_TAG, persistedMovementPolicy);
            }
            compoundTag.putBoolean(ALLOW_WORLD_ACTIONS_TAG, allowWorldActions);
        }
        if (stashMemory != null) {
            CompoundTag stashTag = new CompoundTag();
            stashTag.putString(STASH_DIMENSION_TAG, stashMemory.dimensionId());
            stashTag.putInt(STASH_X_TAG, stashMemory.blockPos().getX());
            stashTag.putInt(STASH_Y_TAG, stashMemory.blockPos().getY());
            stashTag.putInt(STASH_Z_TAG, stashMemory.blockPos().getZ());
            compoundTag.put(STASH_MEMORY_TAG, stashTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        for (int slot = 0; slot < internalInventory.size(); slot++) {
            internalInventory.set(slot, ItemStack.EMPTY);
        }
        ListTag inventoryTag = compoundTag.getList(INVENTORY_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < inventoryTag.size(); index++) {
            CompoundTag itemTag = inventoryTag.getCompound(index);
            int slot = itemTag.getByte(INVENTORY_SLOT_TAG) & 255;
            if (slot >= 0 && slot < internalInventory.size()) {
                ItemStack itemStack = ItemStack.of(itemTag);
                if (!itemStack.isEmpty()) {
                    internalInventory.set(slot, itemStack);
                }
            }
        }
        if (compoundTag.contains(SELECTED_MAIN_HAND_SLOT_TAG, Tag.TAG_INT)) {
            selectedMainHandSlot = validatedMainHandSlot(compoundTag.getInt(SELECTED_MAIN_HAND_SLOT_TAG));
        } else {
            selectedMainHandSlot = DEFAULT_SELECTED_MAIN_HAND_SLOT;
        }
        persistedOwnerId = compoundTag.hasUUID(OWNER_ID_TAG) ? compoundTag.getUUID(OWNER_ID_TAG) : null;
        persistedRoleId = compoundTag.contains(ROLE_ID_TAG, Tag.TAG_STRING) ? compoundTag.getString(ROLE_ID_TAG) : null;
        persistedProfileName = compoundTag.contains(PROFILE_NAME_TAG, Tag.TAG_STRING)
                ? compoundTag.getString(PROFILE_NAME_TAG)
                : null;
        persistedProfileSkinTexture = compoundTag.contains(PROFILE_SKIN_TEXTURE_TAG, Tag.TAG_STRING)
                ? compoundTag.getString(PROFILE_SKIN_TEXTURE_TAG)
                : null;
        if (persistedProfileSkinTexture != null && persistedProfileSkinTexture.isBlank()) {
            persistedProfileSkinTexture = null;
        }
        persistedMovementPolicy = compoundTag.contains(MOVEMENT_POLICY_TAG, Tag.TAG_STRING)
                ? compoundTag.getString(MOVEMENT_POLICY_TAG)
                : null;
        if (persistedMovementPolicy != null && persistedMovementPolicy.isBlank()) {
            persistedMovementPolicy = null;
        }
        allowWorldActions = compoundTag.contains(ALLOW_WORLD_ACTIONS_TAG, Tag.TAG_BYTE)
                && compoundTag.getBoolean(ALLOW_WORLD_ACTIONS_TAG);
        stashMemory = readStashMemory(compoundTag);
        syncPersistedIdentity();
        if (persistedOwnerId != null) {
            runtimeCommandExecutor.setOwnerId(new NpcOwnerId(persistedOwnerId));
        }
    }

    protected void syncPersistedIdentity() {
        entityData.set(DATA_OWNER_ID, Optional.ofNullable(persistedOwnerId));
        entityData.set(DATA_ROLE_ID, persistedRoleId == null ? "" : persistedRoleId);
        entityData.set(DATA_PROFILE_NAME, persistedProfileName == null ? "" : persistedProfileName);
        entityData.set(DATA_PROFILE_SKIN_TEXTURE, persistedProfileSkinTexture == null ? "" : persistedProfileSkinTexture);
    }

    protected int inventorySlotForEquipmentSlot(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case MAINHAND -> selectedMainHandSlot;
            case OFFHAND -> OFFHAND_SLOT;
            case FEET -> ARMOR_FEET_SLOT;
            case LEGS -> ARMOR_LEGS_SLOT;
            case CHEST -> ARMOR_CHEST_SLOT;
            case HEAD -> ARMOR_HEAD_SLOT;
        };
    }

    private int validatedMainHandSlot(int slot) {
        return NpcHotbarSelection.validatedHotbarSlot(slot);
    }

    protected List<ItemStack> hotbarItems() {
        List<ItemStack> items = new ArrayList<>(HOTBAR_SLOT_COUNT);
        for (int slot = FIRST_NORMAL_INVENTORY_SLOT; slot < HOTBAR_SLOT_COUNT; slot++) {
            items.add(internalInventory.get(slot));
        }
        return items;
    }

    protected void restoreInternalInventory(List<ItemStack> snapshot) {
        for (int slot = 0; slot < internalInventory.size() && slot < snapshot.size(); slot++) {
            internalInventory.set(slot, snapshot.get(slot).copy());
        }
    }

    private static StashMemory readStashMemory(CompoundTag compoundTag) {
        if (!compoundTag.contains(STASH_MEMORY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag stashTag = compoundTag.getCompound(STASH_MEMORY_TAG);
        if (!stashTag.contains(STASH_DIMENSION_TAG, Tag.TAG_STRING)
                || !stashTag.contains(STASH_X_TAG, Tag.TAG_INT)
                || !stashTag.contains(STASH_Y_TAG, Tag.TAG_INT)
                || !stashTag.contains(STASH_Z_TAG, Tag.TAG_INT)) {
            return null;
        }
        String dimensionId = stashTag.getString(STASH_DIMENSION_TAG).trim();
        if (dimensionId.isEmpty()) {
            return null;
        }
        return new StashMemory(
                dimensionId,
                new BlockPos(stashTag.getInt(STASH_X_TAG), stashTag.getInt(STASH_Y_TAG), stashTag.getInt(STASH_Z_TAG))
        );
    }

    protected void restorePlayerMainInventory(ServerPlayer player, List<ItemStack> snapshot) {
        for (int slot = 0; slot < player.getInventory().items.size() && slot < snapshot.size(); slot++) {
            player.getInventory().items.set(slot, snapshot.get(slot).copy());
        }
    }

    protected double toolScore(ItemStack itemStack, BlockState blockState) {
        if (itemStack.isEmpty()) {
            return 0.0D;
        }
        float destroySpeed = itemStack.getDestroySpeed(blockState);
        if (destroySpeed <= 1.0F && !itemStack.isCorrectToolForDrops(blockState)) {
            return 0.0D;
        }
        double score = destroySpeed;
        if (itemStack.isCorrectToolForDrops(blockState)) {
            score += 1000.0D;
        }
        return score;
    }

    protected boolean isAICoreControl(String control) {
        return control != null && switch (control) {
            case "forward", "back", "left", "right", "jump", "sprint", "sneak" -> true;
            default -> false;
        };
    }

    protected void collectNearbyItems() {
        List<ItemEntity> itemEntities = level().getEntitiesOfClass(
                ItemEntity.class,
                getBoundingBox().inflate(ITEM_PICKUP_RANGE),
                itemEntity -> itemEntity.isAlive() && !itemEntity.hasPickUpDelay() && !itemEntity.getItem().isEmpty()
        );
        for (ItemEntity itemEntity : itemEntities) {
            ItemStack remainingStack = insertIntoInternalInventory(itemEntity.getItem().copy());
            if (remainingStack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainingStack);
            }
        }
    }

    private ItemStack insertIntoInternalInventory(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainingStack = itemStack.copy();
        mergeIntoExistingStacks(remainingStack);
        fillEmptySlots(remainingStack);
        return remainingStack;
    }

    private void mergeIntoExistingStacks(ItemStack remainingStack) {
        for (int slot = FIRST_NORMAL_INVENTORY_SLOT;
             slot < FIRST_EQUIPMENT_INVENTORY_SLOT && !remainingStack.isEmpty();
             slot++) {
            ItemStack existingStack = internalInventory.get(slot);
            if (!existingStack.isEmpty() && ItemStack.isSameItemSameTags(existingStack, remainingStack)) {
                int movableCount = Math.min(remainingStack.getCount(), existingStack.getMaxStackSize() - existingStack.getCount());
                if (movableCount > 0) {
                    existingStack.grow(movableCount);
                    remainingStack.shrink(movableCount);
                }
            }
        }
    }

    private void fillEmptySlots(ItemStack remainingStack) {
        for (int slot = FIRST_NORMAL_INVENTORY_SLOT;
             slot < FIRST_EQUIPMENT_INVENTORY_SLOT && !remainingStack.isEmpty();
             slot++) {
            ItemStack existingStack = internalInventory.get(slot);
            if (existingStack.isEmpty()) {
                ItemStack insertedStack = remainingStack.copy();
                insertedStack.setCount(Math.min(remainingStack.getCount(), insertedStack.getMaxStackSize()));
                internalInventory.set(slot, insertedStack);
                remainingStack.shrink(insertedStack.getCount());
            }
        }
    }
}
