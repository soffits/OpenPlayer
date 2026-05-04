package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationControllerSnapshot;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpenPlayerNpcEntity extends PathfinderMob {
    private static final String INVENTORY_TAG = "OpenPlayerInventory";
    private static final String INVENTORY_SLOT_TAG = "Slot";
    private static final String SELECTED_MAIN_HAND_SLOT_TAG = "OpenPlayerSelectedMainHandSlot";
    private static final String OWNER_ID_TAG = "OpenPlayerOwnerId";
    private static final String ROLE_ID_TAG = "OpenPlayerRoleId";
    private static final String PROFILE_NAME_TAG = "OpenPlayerProfileName";
    private static final String PROFILE_SKIN_TEXTURE_TAG = "OpenPlayerProfileSkinTexture";
    private static final String ALLOW_WORLD_ACTIONS_TAG = "OpenPlayerAllowWorldActions";
    private static final String STASH_MEMORY_TAG = "OpenPlayerStashMemory";
    private static final String STASH_DIMENSION_TAG = "Dimension";
    private static final String STASH_X_TAG = "X";
    private static final String STASH_Y_TAG = "Y";
    private static final String STASH_Z_TAG = "Z";
    private static final int INVENTORY_SIZE = 36;
    private static final int FIRST_NORMAL_INVENTORY_SLOT = 0;
    private static final int HOTBAR_SLOT_COUNT = NpcHotbarSelection.HOTBAR_SIZE;
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
    private static final EntityDataAccessor<String> DATA_PROFILE_SKIN_TEXTURE = SynchedEntityData.defineId(
            OpenPlayerNpcEntity.class,
            EntityDataSerializers.STRING
    );

    private final RuntimeCommandExecutor runtimeCommandExecutor = new RuntimeCommandExecutor(this);
    private final AICoreNpcSessionState aicoreSessionState = new AICoreNpcSessionState();
    private final NonNullList<ItemStack> internalInventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final Map<String, Boolean> aicoreControlStates = new LinkedHashMap<>();
    private UUID persistedOwnerId;
    private String persistedRoleId;
    private String persistedProfileName;
    private String persistedProfileSkinTexture;
    private boolean allowWorldActions;
    private int selectedMainHandSlot = DEFAULT_SELECTED_MAIN_HAND_SLOT;
    private StashMemory stashMemory;

    public OpenPlayerNpcEntity(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
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
        if (!level().isClientSide && allowWorldActions) {
            collectNearbyItems();
        }
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
    }

    public AutomationControllerSnapshot runtimeCommandSnapshot() {
        return runtimeCommandExecutor.snapshot();
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

    public void rememberStash(String dimensionId, BlockPos blockPos) {
        if (dimensionId == null || dimensionId.isBlank() || blockPos == null) {
            stashMemory = null;
            return;
        }
        stashMemory = new StashMemory(dimensionId.trim(), blockPos.immutable());
    }

    public BlockPos rememberedStashPos(String dimensionId) {
        if (dimensionId == null || stashMemory == null || !stashMemory.dimensionId().equals(dimensionId)) {
            return null;
        }
        return stashMemory.blockPos();
    }

    public boolean equipMatchingItem(Item item) {
        if (item == null) {
            return false;
        }
        int slot = NpcInventoryTransfer.firstHotbarSlotMatchingItem(internalInventory, item);
        return slot >= 0 && selectHotbarSlot(slot);
    }

    public boolean dropInventoryItem(Item item, int count) {
        if (item == null || count < 1) {
            return false;
        }
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(internalInventory);
        ItemStack droppedStack = NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                count,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
        if (droppedStack.isEmpty()) {
            restoreInternalInventory(snapshot);
            return false;
        }
        ItemEntity itemEntity = spawnAtLocation(droppedStack, 0.0F);
        if (itemEntity == null) {
            restoreInternalInventory(snapshot);
            return false;
        }
        itemEntity.setPickUpDelay(40);
        return true;
    }

    public boolean consumeOneNormalInventoryItem(Item item) {
        if (item == null) {
            return false;
        }
        return !NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                1,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        ).isEmpty();
    }

    public boolean giveInventoryItemToPlayer(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || count < 1) {
            return false;
        }
        ItemStack transferStack = new ItemStack(item, count);
        List<ItemStack> playerInventorySnapshot = NpcInventoryTransfer.copyStacks(player.getInventory().items);
        if (!NpcInventoryTransfer.canInsertAll(
                playerInventorySnapshot,
                transferStack,
                0,
                playerInventorySnapshot.size()
        )) {
            return false;
        }

        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(internalInventory);
        List<ItemStack> livePlayerSnapshot = NpcInventoryTransfer.copyStacks(player.getInventory().items);
        ItemStack extractedStack = NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                count,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
        if (extractedStack.isEmpty()) {
            restoreInternalInventory(npcSnapshot);
            return false;
        }
        ItemStack remainingStack = extractedStack.copy();
        boolean accepted = player.getInventory().add(remainingStack) && remainingStack.isEmpty();
        if (!accepted) {
            restoreInternalInventory(npcSnapshot);
            restorePlayerMainInventory(player, livePlayerSnapshot);
            return false;
        }
        return true;
    }

    public boolean swapSelectedHotbarStackToOffhand() {
        ItemStack selectedStack = getMainHandItem();
        if (selectedStack.isEmpty()) {
            return false;
        }
        ItemStack offhandStack = getItemBySlot(EquipmentSlot.OFFHAND);
        internalInventory.set(selectedMainHandSlot, offhandStack.copy());
        internalInventory.set(OFFHAND_SLOT, selectedStack.copy());
        return true;
    }

    public boolean unequipToNormalInventory(EquipmentSlot equipmentSlot) {
        int equipmentInventorySlot = inventorySlotForEquipmentSlot(equipmentSlot);
        if (equipmentInventorySlot < FIRST_EQUIPMENT_INVENTORY_SLOT || equipmentInventorySlot >= internalInventory.size()) {
            return false;
        }
        ItemStack equippedStack = internalInventory.get(equipmentInventorySlot);
        if (equippedStack.isEmpty()) {
            return false;
        }
        List<ItemStack> snapshot = inventorySnapshot();
        internalInventory.set(equipmentInventorySlot, ItemStack.EMPTY);
        if (!NpcInventoryTransfer.insertAll(internalInventory, equippedStack.copy(), FIRST_NORMAL_INVENTORY_SLOT, FIRST_EQUIPMENT_INVENTORY_SLOT)) {
            restoreInternalInventory(snapshot);
            return false;
        }
        return true;
    }

    private int firstNormalInventorySlotMatching(Item item, int startSlotInclusive, int endSlotExclusive) {
        int end = Math.min(endSlotExclusive, internalInventory.size());
        for (int slot = Math.max(FIRST_NORMAL_INVENTORY_SLOT, startSlotInclusive); slot < end; slot++) {
            ItemStack stack = internalInventory.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        return -1;
    }

    public void swingMainHandAction() {
        swing(InteractionHand.MAIN_HAND);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D)
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
        allowWorldActions = compoundTag.contains(ALLOW_WORLD_ACTIONS_TAG, Tag.TAG_BYTE)
                && compoundTag.getBoolean(ALLOW_WORLD_ACTIONS_TAG);
        stashMemory = readStashMemory(compoundTag);
        syncPersistedIdentity();
        if (persistedOwnerId != null) {
            runtimeCommandExecutor.setOwnerId(new NpcOwnerId(persistedOwnerId));
        }
    }

    private void syncPersistedIdentity() {
        entityData.set(DATA_OWNER_ID, Optional.ofNullable(persistedOwnerId));
        entityData.set(DATA_ROLE_ID, persistedRoleId == null ? "" : persistedRoleId);
        entityData.set(DATA_PROFILE_NAME, persistedProfileName == null ? "" : persistedProfileName);
        entityData.set(DATA_PROFILE_SKIN_TEXTURE, persistedProfileSkinTexture == null ? "" : persistedProfileSkinTexture);
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
        return NpcHotbarSelection.validatedHotbarSlot(slot);
    }

    private List<ItemStack> hotbarItems() {
        List<ItemStack> items = new ArrayList<>(HOTBAR_SLOT_COUNT);
        for (int slot = FIRST_NORMAL_INVENTORY_SLOT; slot < HOTBAR_SLOT_COUNT; slot++) {
            items.add(internalInventory.get(slot));
        }
        return items;
    }

    private void restoreInternalInventory(List<ItemStack> snapshot) {
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

    private record StashMemory(String dimensionId, BlockPos blockPos) {
    }

    private static void restorePlayerMainInventory(ServerPlayer player, List<ItemStack> snapshot) {
        for (int slot = 0; slot < player.getInventory().items.size() && slot < snapshot.size(); slot++) {
            player.getInventory().items.set(slot, snapshot.get(slot).copy());
        }
    }

    private static double toolScore(ItemStack itemStack, BlockState blockState) {
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

    private static boolean isAICoreControl(String control) {
        return control != null && switch (control) {
            case "forward", "back", "left", "right", "jump", "sprint", "sneak" -> true;
            default -> false;
        };
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
