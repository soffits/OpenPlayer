package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationControllerSnapshot;
import dev.soffits.openplayer.aicore.AICoreNpcSessionState;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

abstract class OpenPlayerNpcEntityInventoryBase extends PathfinderMob {
    protected static final String INVENTORY_TAG = "OpenPlayerInventory";
    protected static final String INVENTORY_SLOT_TAG = "Slot";
    protected static final String SELECTED_MAIN_HAND_SLOT_TAG = "OpenPlayerSelectedMainHandSlot";
    protected static final String OWNER_ID_TAG = "OpenPlayerOwnerId";
    protected static final String ROLE_ID_TAG = "OpenPlayerRoleId";
    protected static final String PROFILE_NAME_TAG = "OpenPlayerProfileName";
    protected static final String PROFILE_SKIN_TEXTURE_TAG = "OpenPlayerProfileSkinTexture";
    protected static final String MOVEMENT_POLICY_TAG = "OpenPlayerMovementPolicy";
    protected static final String ALLOW_WORLD_ACTIONS_TAG = "OpenPlayerAllowWorldActions";
    protected static final String STASH_MEMORY_TAG = "OpenPlayerStashMemory";
    protected static final String STASH_DIMENSION_TAG = "Dimension";
    protected static final String STASH_X_TAG = "X";
    protected static final String STASH_Y_TAG = "Y";
    protected static final String STASH_Z_TAG = "Z";
    protected static final int INVENTORY_SIZE = 36;
    protected static final int FIRST_NORMAL_INVENTORY_SLOT = 0;
    protected static final int HOTBAR_SLOT_COUNT = NpcHotbarSelection.HOTBAR_SIZE;
    protected static final int NORMAL_INVENTORY_SLOT_COUNT = 31;
    protected static final int FIRST_EQUIPMENT_INVENTORY_SLOT = FIRST_NORMAL_INVENTORY_SLOT
            + NORMAL_INVENTORY_SLOT_COUNT;
    protected static final int DEFAULT_SELECTED_MAIN_HAND_SLOT = 0;
    protected static final int ARMOR_FEET_SLOT = FIRST_EQUIPMENT_INVENTORY_SLOT;
    protected static final int ARMOR_LEGS_SLOT = 32;
    protected static final int ARMOR_CHEST_SLOT = 33;
    protected static final int ARMOR_HEAD_SLOT = 34;
    protected static final int OFFHAND_SLOT = 35;
    protected static final double ITEM_PICKUP_RANGE = 1.0D;
    protected static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_ID = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.OPTIONAL_UUID
    );
    protected static final EntityDataAccessor<String> DATA_ROLE_ID = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );
    protected static final EntityDataAccessor<String> DATA_PROFILE_NAME = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );
    protected static final EntityDataAccessor<String> DATA_PROFILE_SKIN_TEXTURE = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );

    protected final RuntimeCommandExecutor runtimeCommandExecutor = new RuntimeCommandExecutor((OpenPlayerNpcEntity) (Object) this);
    protected final AICoreNpcSessionState aicoreSessionState = new AICoreNpcSessionState();
    protected final NpcActiveChunkTickets activeChunkTickets = new NpcActiveChunkTickets(getUUID());
    protected final NonNullList<ItemStack> internalInventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    protected final Map<String, Boolean> aicoreControlStates = new LinkedHashMap<>();
    protected UUID persistedOwnerId;
    protected String persistedRoleId;
    protected String persistedProfileName;
    protected String persistedProfileSkinTexture;
    protected String persistedMovementPolicy;
    protected boolean allowWorldActions;
    protected int selectedMainHandSlot = DEFAULT_SELECTED_MAIN_HAND_SLOT;
    protected StashMemory stashMemory;

    protected OpenPlayerNpcEntityInventoryBase(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER_ID, Optional.empty());
        entityData.define(DATA_ROLE_ID, "");
        entityData.define(DATA_PROFILE_NAME, "");
        entityData.define(DATA_PROFILE_SKIN_TEXTURE, "");
    }

    @Override
    public void tick() {
        super.tick();
        runtimeCommandExecutor.tick();
        if (!level().isClientSide) {
            updateActiveChunkTickets();
            if (allowWorldActions) {
                collectNearbyItems();
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        activeChunkTickets.release();
        super.remove(reason);
    }

    public void setRuntimeOwnerId(NpcOwnerId ownerId) {
        runtimeCommandExecutor.setOwnerId(ownerId);
    }

    public void setPersistedIdentity(NpcOwnerId ownerId, String roleId, String profileName) {
        setPersistedIdentity(ownerId, roleId, profileName, null);
    }

    public void setPersistedIdentity(NpcOwnerId ownerId, String roleId, String profileName, String profileSkinTexture) {
        setPersistedIdentity(ownerId, roleId, profileName, profileSkinTexture, false);
    }

    public void setPersistedIdentity(NpcOwnerId ownerId, String roleId, String profileName, String profileSkinTexture,
                                      boolean allowWorldActions) {
        setPersistedIdentity(ownerId, roleId, profileName, profileSkinTexture, allowWorldActions, null);
    }

    public void setPersistedIdentity(NpcOwnerId ownerId, String roleId, String profileName, String profileSkinTexture,
                                     boolean allowWorldActions, String movementPolicy) {
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
        persistedProfileSkinTexture = profileSkinTexture == null || profileSkinTexture.isBlank() ? null : profileSkinTexture;
        persistedMovementPolicy = movementPolicy == null || movementPolicy.isBlank() ? null : movementPolicy;
        this.allowWorldActions = allowWorldActions;
        syncPersistedIdentity();
        setRuntimeOwnerId(ownerId);
    }

    public boolean allowWorldActions() {
        return allowWorldActions;
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

    public Optional<String> persistedProfileSkinTexture() {
        String syncedProfileSkinTexture = entityData.get(DATA_PROFILE_SKIN_TEXTURE);
        if (!syncedProfileSkinTexture.isBlank()) {
            return Optional.of(syncedProfileSkinTexture);
        }
        return Optional.ofNullable(persistedProfileSkinTexture);
    }

    public Optional<String> persistedMovementPolicy() {
        return Optional.ofNullable(persistedMovementPolicy);
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

    public AICoreNpcSessionState aicoreSessionState() {
        return aicoreSessionState;
    }

    public void stopRuntimeCommands() {
        runtimeCommandExecutor.stopAll();
        activeChunkTickets.release();
    }

    public AutomationControllerSnapshot runtimeCommandSnapshot() {
        return runtimeCommandExecutor.snapshot();
    }

    private void updateActiveChunkTickets() {
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) {
            activeChunkTickets.release();
            return;
        }
        AutomationControllerSnapshot snapshot = runtimeCommandExecutor.snapshot();
        if (snapshot.active() || snapshot.queuedCommandCount() > 0) {
            activeChunkTickets.update(serverLevel, blockPosition());
            return;
        }
        activeChunkTickets.release();
    }

    public int selectedHotbarSlot() {
        return selectedMainHandSlot;
    }

    public boolean selectHotbarSlot(int slot) {
        if (!NpcHotbarSelection.isHotbarSlot(slot)) {
            return false;
        }
        selectedMainHandSlot = slot;
        return true;
    }

    public boolean setAICoreControlState(String control, boolean state) {
        if (!isAICoreControl(control)) {
            return false;
        }
        aicoreControlStates.put(control, state);
        return true;
    }

    public boolean aicoreControlState(String control) {
        return isAICoreControl(control) && aicoreControlStates.getOrDefault(control, false);
    }

    public void clearAICoreControlStates() {
        aicoreControlStates.clear();
    }

    public ItemStack getInventoryItem(int slot) {
        if (slot < 0 || slot >= internalInventory.size()) {
            return ItemStack.EMPTY;
        }
        return internalInventory.get(slot);
    }

    public boolean setInventoryItem(int slot, ItemStack itemStack) {
        if (slot < 0 || slot >= internalInventory.size() || itemStack == null) {
            return false;
        }
        internalInventory.set(slot, itemStack.copy());
        return true;
    }

    public boolean selectBestToolFor(BlockState blockState, Level level, BlockPos blockPos) {
        if (blockState == null || level == null || blockPos == null) {
            return false;
        }
        int slot = NpcHotbarSelection.bestScoredSlot(hotbarItems(), itemStack -> toolScore(itemStack, blockState));
        return slot >= 0 && selectHotbarSlot(slot);
    }

    public boolean selectFirstHotbarBlockItem() {
        return selectFirstHotbarItem(itemStack -> itemStack.getItem() instanceof BlockItem);
    }

    public boolean selectFirstHotbarItem(Predicate<ItemStack> predicate) {
        if (predicate == null) {
            return false;
        }
        int slot = NpcHotbarSelection.firstMatchingSlot(hotbarItems(), itemStack -> !itemStack.isEmpty() && predicate.test(itemStack));
        return slot >= 0 && selectHotbarSlot(slot);
    }

    public boolean selectOrMoveNormalItemToHotbar(Item item) {
        if (item == null) {
            return false;
        }
        int hotbarSlot = NpcInventoryTransfer.firstHotbarSlotMatchingItem(internalInventory, item);
        if (hotbarSlot >= 0) {
            return selectHotbarSlot(hotbarSlot);
        }
        int inventorySlot = firstNormalInventorySlotMatching(item, HOTBAR_SLOT_COUNT, FIRST_EQUIPMENT_INVENTORY_SLOT);
        if (inventorySlot < 0) {
            return false;
        }
        ItemStack selectedStack = internalInventory.get(selectedMainHandSlot).copy();
        internalInventory.set(selectedMainHandSlot, internalInventory.get(inventorySlot).copy());
        internalInventory.set(inventorySlot, selectedStack);
        return true;
    }

    protected abstract void collectNearbyItems();

    protected abstract void syncPersistedIdentity();

    protected abstract boolean isAICoreControl(String control);

    protected abstract List<ItemStack> hotbarItems();

    protected abstract double toolScore(ItemStack itemStack, BlockState blockState);

    protected abstract int firstNormalInventorySlotMatching(Item item, int startSlotInclusive, int endSlotExclusive);

    public boolean dropSelectedHotbarStack() {
        ItemStack droppedStack = NpcInventoryTransfer.selectedHotbarDropStackOrEmpty(internalInventory, selectedMainHandSlot);
        if (droppedStack.isEmpty()) {
            return false;
        }
        ItemEntity itemEntity = spawnAtLocation(droppedStack, 0.0F);
        if (itemEntity == null) {
            return false;
        }
        if (!NpcInventoryTransfer.commitSelectedHotbarDrop(internalInventory, selectedMainHandSlot, true)) {
            return false;
        }
        itemEntity.setPickUpDelay(40);
        return true;
    }

    public String inventorySummary() {
        return NpcInventorySummary.format(internalInventory, selectedMainHandSlot);
    }

    public List<ItemStack> inventorySnapshot() {
        return NpcInventoryTransfer.copyStacks(internalInventory);
    }

    public int normalInventoryCount(Item item) {
        if (item == null) {
            return 0;
        }
        return NpcInventoryTransfer.countItem(
                internalInventory,
                item,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
    }

    public boolean depositAllNormalInventoryTo(List<ItemStack> containerStacks) {
        return NpcInventoryTransfer.depositAllNormalInventory(internalInventory, containerStacks);
    }

    public boolean depositInventoryItemTo(List<ItemStack> containerStacks, Item item, int count) {
        return NpcInventoryTransfer.depositExactItem(internalInventory, containerStacks, item, count);
    }

    public boolean withdrawInventoryItemFrom(List<ItemStack> containerStacks, Item item, int count) {
        return NpcInventoryTransfer.withdrawExactItem(internalInventory, containerStacks, item, count);
    }

    public boolean craftInventoryRecipeNoLoss(List<Ingredient> ingredients, ItemStack result, int crafts) {
        return NpcInventoryTransfer.craftNormalInventory(internalInventory, ingredients, result, crafts);
    }

    public boolean moveInventorySlotItemNoLoss(int sourceSlot, int destinationSlot) {
        if (sourceSlot < 0 || sourceSlot >= internalInventory.size()
                || destinationSlot < 0 || destinationSlot >= internalInventory.size()
                || sourceSlot == destinationSlot) {
            return false;
        }
        ItemStack sourceStack = internalInventory.get(sourceSlot);
        if (sourceStack.isEmpty()) {
            return false;
        }
        ItemStack destinationStack = internalInventory.get(destinationSlot);
        if (destinationStack.isEmpty()) {
            internalInventory.set(destinationSlot, sourceStack.copy());
            internalInventory.set(sourceSlot, ItemStack.EMPTY);
            return true;
        }
        if (!ItemStack.isSameItemSameTags(sourceStack, destinationStack)) {
            return false;
        }
        int capacity = destinationStack.getMaxStackSize() - destinationStack.getCount();
        if (capacity < sourceStack.getCount()) {
            return false;
        }
        destinationStack.grow(sourceStack.getCount());
        internalInventory.set(sourceSlot, ItemStack.EMPTY);
        return true;
    }

}
