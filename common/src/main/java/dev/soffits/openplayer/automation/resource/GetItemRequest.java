package dev.soffits.openplayer.automation.resource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record GetItemRequest(ResourceLocation itemId, Item item, int count) {
    public GetItemRequest {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId cannot be null");
        }
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("item must be a registered non-air item");
        }
        if (count < 1 || count > item.getDefaultInstance().getMaxStackSize()) {
            throw new IllegalArgumentException("count must fit in one item stack");
        }
    }

    public static GetItemRequest of(Item item, int count) {
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("item must be a registered non-air item");
        }
        return new GetItemRequest(BuiltInRegistries.ITEM.getKey(item), item, count);
    }
}
