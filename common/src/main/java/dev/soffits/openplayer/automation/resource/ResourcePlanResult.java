package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public record ResourcePlanResult(Status status, List<ResourcePlanStep> steps, List<ItemStack> missingItems, String reason) {
    public enum Status {
        ALREADY_AVAILABLE,
        CRAFTING_STEPS,
        MISSING_MATERIALS,
        UNSUPPORTED_TARGET
    }

    public ResourcePlanResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        steps = copySteps(steps);
        missingItems = copyStacks(missingItems);
        reason = reason == null ? "" : reason.trim();
    }

    public static ResourcePlanResult alreadyAvailable() {
        return new ResourcePlanResult(Status.ALREADY_AVAILABLE, List.of(), List.of(), "");
    }

    public static ResourcePlanResult craftingSteps(List<ResourcePlanStep> steps) {
        return new ResourcePlanResult(Status.CRAFTING_STEPS, steps, List.of(), "");
    }

    public static ResourcePlanResult missingMaterials(List<ItemStack> missingItems) {
        return new ResourcePlanResult(Status.MISSING_MATERIALS, List.of(), missingItems, "missing materials");
    }

    public static ResourcePlanResult unsupportedTarget() {
        return unsupportedTarget("unsupported/no recipe path");
    }

    public static ResourcePlanResult unsupportedTarget(String reason) {
        return new ResourcePlanResult(Status.UNSUPPORTED_TARGET, List.of(), List.of(), reason);
    }

    private static List<ResourcePlanStep> copySteps(List<ResourcePlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return List.copyOf(steps);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }
}
