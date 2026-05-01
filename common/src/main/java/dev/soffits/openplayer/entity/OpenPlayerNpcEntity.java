package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class OpenPlayerNpcEntity extends PathfinderMob {
    private static final String INVENTORY_TAG = "OpenPlayerInventory";
    private static final String INVENTORY_SLOT_TAG = "Slot";
    private static final String OWNER_ID_TAG = "OpenPlayerOwnerId";
    private static final String ROLE_ID_TAG = "OpenPlayerRoleId";
    private static final String PROFILE_NAME_TAG = "OpenPlayerProfileName";
    private static final int INVENTORY_SIZE = 36;
    private static final double ITEM_PICKUP_RANGE = 1.0D;

    private final RuntimeCommandExecutor runtimeCommandExecutor = new RuntimeCommandExecutor(this);
    private final NonNullList<ItemStack> internalInventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private UUID persistedOwnerId;
    private String persistedRoleId;
    private String persistedProfileName;

    public OpenPlayerNpcEntity(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        runtimeCommandExecutor.tick();
        if (!level().isClientSide) {
            collectNearbyItems();
        }
    }

    public void setRuntimeOwnerId(NpcOwnerId ownerId) {
        runtimeCommandExecutor.setOwnerId(ownerId);
    }

    public void setPersistedIdentity(NpcOwnerId ownerId, String roleId, String profileName) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("roleId cannot be blank");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("profileName cannot be blank");
        }
        persistedOwnerId = ownerId.value();
        persistedRoleId = roleId;
        persistedProfileName = profileName;
        setRuntimeOwnerId(ownerId);
    }

    public Optional<UUID> persistedOwnerId() {
        return Optional.ofNullable(persistedOwnerId);
    }

    public Optional<String> persistedRoleId() {
        return Optional.ofNullable(persistedRoleId);
    }

    public Optional<String> persistedProfileName() {
        return Optional.ofNullable(persistedProfileName);
    }

    public boolean hasValidPersistedIdentity() {
        return persistedOwnerId != null
                && persistedRoleId != null
                && !persistedRoleId.isBlank()
                && persistedProfileName != null
                && !persistedProfileName.isBlank();
    }

    public CommandSubmissionResult submitRuntimeCommand(AiPlayerNpcCommand command) {
        return runtimeCommandExecutor.submit(command);
    }

    public void stopRuntimeCommands() {
        runtimeCommandExecutor.stopAll();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
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
        if (hasValidPersistedIdentity()) {
            compoundTag.putUUID(OWNER_ID_TAG, persistedOwnerId);
            compoundTag.putString(ROLE_ID_TAG, persistedRoleId);
            compoundTag.putString(PROFILE_NAME_TAG, persistedProfileName);
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
        persistedOwnerId = compoundTag.hasUUID(OWNER_ID_TAG) ? compoundTag.getUUID(OWNER_ID_TAG) : null;
        persistedRoleId = compoundTag.contains(ROLE_ID_TAG, Tag.TAG_STRING) ? compoundTag.getString(ROLE_ID_TAG) : null;
        persistedProfileName = compoundTag.contains(PROFILE_NAME_TAG, Tag.TAG_STRING)
                ? compoundTag.getString(PROFILE_NAME_TAG)
                : null;
        if (persistedOwnerId != null) {
            runtimeCommandExecutor.setOwnerId(new NpcOwnerId(persistedOwnerId));
        }
    }

    private void collectNearbyItems() {
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
        for (int slot = 0; slot < internalInventory.size() && !remainingStack.isEmpty(); slot++) {
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
        for (int slot = 0; slot < internalInventory.size() && !remainingStack.isEmpty(); slot++) {
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
