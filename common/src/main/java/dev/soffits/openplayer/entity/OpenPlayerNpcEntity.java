package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class OpenPlayerNpcEntity extends PathfinderMob {
    private static final String INVENTORY_TAG = "OpenPlayerInventory";
    private static final String INVENTORY_SLOT_TAG = "Slot";
    private static final String SELECTED_MAIN_HAND_SLOT_TAG = "OpenPlayerSelectedMainHandSlot";
    private static final String OWNER_ID_TAG = "OpenPlayerOwnerId";
    private static final String ROLE_ID_TAG = "OpenPlayerRoleId";
    private static final String PROFILE_NAME_TAG = "OpenPlayerProfileName";
    private static final int INVENTORY_SIZE = 36;
    private static final int FIRST_NORMAL_INVENTORY_SLOT = 0;
    private static final int NORMAL_INVENTORY_SLOT_COUNT = 31;
    private static final int FIRST_EQUIPMENT_INVENTORY_SLOT = FIRST_NORMAL_INVENTORY_SLOT
            + NORMAL_INVENTORY_SLOT_COUNT;
    private static final int DEFAULT_SELECTED_MAIN_HAND_SLOT = 0;
    private static final int ARMOR_FEET_SLOT = FIRST_EQUIPMENT_INVENTORY_SLOT;
    private static final int ARMOR_LEGS_SLOT = 32;
    private static final int ARMOR_CHEST_SLOT = 33;
    private static final int ARMOR_HEAD_SLOT = 34;
    private static final int OFFHAND_SLOT = 35;
    private static final double ITEM_PICKUP_RANGE = 1.0D;
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_ID = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.OPTIONAL_UUID
    );
    private static final EntityDataAccessor<String> DATA_ROLE_ID = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<String> DATA_PROFILE_NAME = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );

    private final RuntimeCommandExecutor runtimeCommandExecutor = new RuntimeCommandExecutor(this);
    private final NonNullList<ItemStack> internalInventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private UUID persistedOwnerId;
    private String persistedRoleId;
    private String persistedProfileName;
    private int selectedMainHandSlot = DEFAULT_SELECTED_MAIN_HAND_SLOT;

    public OpenPlayerNpcEntity(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER_ID, Optional.empty());
        entityData.define(DATA_ROLE_ID, "");
        entityData.define(DATA_PROFILE_NAME, "");
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
        syncPersistedIdentity();
        setRuntimeOwnerId(ownerId);
    }

    public Optional<UUID> persistedOwnerId() {
        return entityData.get(DATA_OWNER_ID).or(() -> Optional.ofNullable(persistedOwnerId));
    }

    public Optional<String> persistedRoleId() {
        String syncedRoleId = entityData.get(DATA_ROLE_ID);
        if (!syncedRoleId.isBlank()) {
            return Optional.of(syncedRoleId);
        }
        return Optional.ofNullable(persistedRoleId);
    }

    public Optional<String> persistedProfileName() {
        String syncedProfileName = entityData.get(DATA_PROFILE_NAME);
        if (!syncedProfileName.isBlank()) {
            return Optional.of(syncedProfileName);
        }
        return Optional.ofNullable(persistedProfileName);
    }

    public boolean hasValidPersistedIdentity() {
        return persistedOwnerId().isPresent()
                && persistedRoleId().isPresent()
                && persistedProfileName().isPresent();
    }

    public UUID deterministicSkinId() {
        Optional<UUID> ownerId = persistedOwnerId();
        Optional<String> roleId = persistedRoleId();
        Optional<String> profileName = persistedProfileName();
        if (ownerId.isEmpty() || roleId.isEmpty() || profileName.isEmpty()) {
            return getUUID();
        }
        String skinKey = ownerId.get() + "\n" + roleId.get() + "\n" + profileName.get();
        return UUID.nameUUIDFromBytes(skinKey.getBytes(StandardCharsets.UTF_8));
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
        syncPersistedIdentity();
        if (persistedOwnerId != null) {
            runtimeCommandExecutor.setOwnerId(new NpcOwnerId(persistedOwnerId));
        }
    }

    private void syncPersistedIdentity() {
        entityData.set(DATA_OWNER_ID, Optional.ofNullable(persistedOwnerId));
        entityData.set(DATA_ROLE_ID, persistedRoleId == null ? "" : persistedRoleId);
        entityData.set(DATA_PROFILE_NAME, persistedProfileName == null ? "" : persistedProfileName);
    }

    private int inventorySlotForEquipmentSlot(EquipmentSlot equipmentSlot) {
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
        if (slot >= FIRST_NORMAL_INVENTORY_SLOT && slot < FIRST_EQUIPMENT_INVENTORY_SLOT) {
            return slot;
        }
        return DEFAULT_SELECTED_MAIN_HAND_SLOT;
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
