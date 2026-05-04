package dev.soffits.openplayer.runtime.context;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record RuntimeAgentSnapshot(
        String status,
        int health,
        int maxHealth,
        int air,
        String food,
        String saturation,
        String activeEffects,
        String physicalStatus,
        String sprintControl,
        String sprinting,
        String mainhand,
        String offhand,
        List<String> armor,
        Map<String, Integer> inventoryCounts
) {
    public RuntimeAgentSnapshot(
            String status,
            int health,
            int maxHealth,
            int air,
            String mainhand,
            String offhand,
            List<String> armor,
            Map<String, Integer> inventoryCounts
    ) {
        this(status, health, maxHealth, air, "unsupported", "unsupported", "none", "unknown", "unsupported",
                "unknown", mainhand, offhand, armor, inventoryCounts);
    }

    public RuntimeAgentSnapshot {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be blank");
        }
        if (health < 0) {
            throw new IllegalArgumentException("health cannot be negative");
        }
        if (maxHealth < 0) {
            throw new IllegalArgumentException("maxHealth cannot be negative");
        }
        if (air < 0) {
            throw new IllegalArgumentException("air cannot be negative");
        }
        if (mainhand == null || mainhand.isBlank()) {
            throw new IllegalArgumentException("mainhand cannot be blank");
        }
        if (offhand == null || offhand.isBlank()) {
            throw new IllegalArgumentException("offhand cannot be blank");
        }
        if (armor == null) {
            throw new IllegalArgumentException("armor cannot be null");
        }
        if (inventoryCounts == null) {
            throw new IllegalArgumentException("inventoryCounts cannot be null");
        }
        status = status.trim();
        food = requireText(food, "food");
        saturation = requireText(saturation, "saturation");
        activeEffects = requireText(activeEffects, "activeEffects");
        physicalStatus = requireText(physicalStatus, "physicalStatus");
        sprintControl = requireText(sprintControl, "sprintControl");
        sprinting = requireText(sprinting, "sprinting");
        mainhand = mainhand.trim();
        offhand = offhand.trim();
        armor = List.copyOf(armor);
        inventoryCounts = copyCounts(inventoryCounts, "inventoryCounts");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static Map<String, Integer> copyCounts(Map<String, Integer> counts, String fieldName) {
        Map<String, Integer> copied = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(fieldName + " cannot contain blank keys");
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(fieldName + " cannot contain negative values");
            }
            if (entry.getValue() > 0) {
                copied.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return Map.copyOf(copied);
    }
}
