package dev.soffits.openplayer.automation.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class BuildPlanParser {
    public static final int MAX_BLOCKS = 64;
    public static final int MAX_DIMENSION = 16;
    public static final String USAGE = "BUILD_STRUCTURE requires instruction: primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>";

    private BuildPlanParser() {
    }

    public static BuildPlan parseOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String trimmed = instruction.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length != 4) {
            return null;
        }
        Map<String, String> values = new HashMap<>();
        for (String token : tokens) {
            int equalsIndex = token.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex != token.lastIndexOf('=') || equalsIndex == token.length() - 1) {
                return null;
            }
            String key = token.substring(0, equalsIndex);
            String value = token.substring(equalsIndex + 1);
            if (!isAllowedKey(key) || values.put(key, value) != null) {
                return null;
            }
        }
        if (values.size() != 4) {
            return null;
        }
        BuildPrimitive primitive = BuildPrimitive.parseOrNull(values.get("primitive"));
        BlockPos origin = parseBlockPosOrNull(values.get("origin"));
        BuildSize size = parseSizeOrNull(values.get("size"));
        ResourceLocation materialId = parseMaterialIdOrNull(values.get("material"));
        if (primitive == null || origin == null || size == null || materialId == null) {
            return null;
        }
        List<BlockPos> positions = positionsOrNull(primitive, origin, size);
        if (positions == null || positions.isEmpty() || positions.size() > MAX_BLOCKS) {
            return null;
        }
        return new BuildPlan(primitive, origin, size, materialId, positions);
    }

    private static boolean isAllowedKey(String key) {
        return key.equals("primitive") || key.equals("origin") || key.equals("size") || key.equals("material");
    }

    private static BlockPos parseBlockPosOrNull(String value) {
        int[] values = parseTripleOrNull(value, false);
        if (values == null) {
            return null;
        }
        return new BlockPos(values[0], values[1], values[2]);
    }

    private static BuildSize parseSizeOrNull(String value) {
        int[] values = parseTripleOrNull(value, true);
        if (values == null) {
            return null;
        }
        return new BuildSize(values[0], values[1], values[2]);
    }

    private static int[] parseTripleOrNull(String value, boolean positiveBounded) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        int[] parsed = new int[3];
        for (int index = 0; index < parts.length; index++) {
            try {
                parsed[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (positiveBounded && (parsed[index] < 1 || parsed[index] > MAX_DIMENSION)) {
                return null;
            }
        }
        return parsed;
    }

    private static ResourceLocation parseMaterialIdOrNull(String value) {
        if (value == null || !value.contains(":")) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    private static List<BlockPos> positionsOrNull(BuildPrimitive primitive, BlockPos origin, BuildSize size) {
        return switch (primitive) {
            case LINE -> linePositionsOrNull(origin, size);
            case WALL -> wallPositionsOrNull(origin, size);
            case FLOOR -> floorPositionsOrNull(origin, size);
            case BOX -> boxPositions(origin, size);
            case STAIRS -> stairPositionsOrNull(origin, size);
        };
    }

    private static List<BlockPos> linePositionsOrNull(BlockPos origin, BuildSize size) {
        int varyingDimensions = varyingDimensionCount(size);
        if (varyingDimensions != 1) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = 0; dx < size.x(); dx++) {
            for (int dy = 0; dy < size.y(); dy++) {
                for (int dz = 0; dz < size.z(); dz++) {
                    positions.add(origin.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> wallPositionsOrNull(BlockPos origin, BuildSize size) {
        if (size.z() != 1) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = 0; dx < size.x(); dx++) {
            for (int dy = 0; dy < size.y(); dy++) {
                positions.add(origin.offset(dx, dy, 0));
            }
        }
        return positions;
    }

    private static List<BlockPos> floorPositionsOrNull(BlockPos origin, BuildSize size) {
        if (size.y() != 1) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = 0; dx < size.x(); dx++) {
            for (int dz = 0; dz < size.z(); dz++) {
                positions.add(origin.offset(dx, 0, dz));
            }
        }
        return positions;
    }

    private static List<BlockPos> boxPositions(BlockPos origin, BuildSize size) {
        Set<BlockPos> unique = new HashSet<>();
        for (int dx = 0; dx < size.x(); dx++) {
            for (int dy = 0; dy < size.y(); dy++) {
                for (int dz = 0; dz < size.z(); dz++) {
                    boolean surface = dx == 0 || dy == 0 || dz == 0
                            || dx == size.x() - 1 || dy == size.y() - 1 || dz == size.z() - 1;
                    if (surface) {
                        unique.add(origin.offset(dx, dy, dz));
                    }
                }
            }
        }
        List<BlockPos> positions = new ArrayList<>(unique);
        positions.sort(BuildPlanParser::compareBlockPos);
        return positions;
    }

    private static List<BlockPos> stairPositionsOrNull(BlockPos origin, BuildSize size) {
        if (size.y() != size.z()) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int step = 0; step < size.y(); step++) {
            for (int dx = 0; dx < size.x(); dx++) {
                positions.add(origin.offset(dx, step, step));
            }
        }
        return positions;
    }

    private static int varyingDimensionCount(BuildSize size) {
        int count = 0;
        if (size.x() > 1) {
            count++;
        }
        if (size.y() > 1) {
            count++;
        }
        if (size.z() > 1) {
            count++;
        }
        return count;
    }

    private static int compareBlockPos(BlockPos first, BlockPos second) {
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) {
            return x;
        }
        return Integer.compare(first.getZ(), second.getZ());
    }
}
