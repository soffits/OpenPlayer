package dev.soffits.openplayer.automation.building;

import net.minecraft.core.BlockPos;

public final class BuildPlanParserTest {
    private BuildPlanParserTest() {
    }

    public static void main(String[] args) {
        parsesLine();
        parsesWall();
        parsesFloor();
        parsesHollowBox();
        parsesStairs();
        rejectsMalformedInstructions();
        rejectsUnboundedPlans();
        parsesNamespacedMaterialId();
    }

    private static void parsesLine() {
        BuildPlan plan = requirePlan("primitive=line origin=1,2,3 size=4,1,1 material=minecraft:cobblestone");
        require(plan.primitive() == BuildPrimitive.LINE, "line primitive expected");
        require(plan.blockCount() == 4, "line should contain four blocks");
        require(plan.positions().get(0).equals(new BlockPos(1, 2, 3)), "line should start at origin");
        require(plan.positions().get(3).equals(new BlockPos(4, 2, 3)), "line should extend on x axis");
    }

    private static void parsesWall() {
        BuildPlan plan = requirePlan("primitive=wall origin=0,64,0 size=3,2,1 material=minecraft:oak_planks");
        require(plan.blockCount() == 6, "wall should contain x*y blocks");
        require(plan.positions().contains(new BlockPos(2, 65, 0)), "wall should include upper corner");
    }

    private static void parsesFloor() {
        BuildPlan plan = requirePlan("primitive=floor origin=0,64,0 size=3,1,4 material=minecraft:stone");
        require(plan.blockCount() == 12, "floor should contain x*z blocks");
        require(plan.positions().contains(new BlockPos(2, 64, 3)), "floor should include far corner");
    }

    private static void parsesHollowBox() {
        BuildPlan plan = requirePlan("primitive=box origin=0,64,0 size=3,3,3 material=minecraft:glass");
        require(plan.blockCount() == 26, "3x3x3 hollow box should omit one center block");
        require(!plan.positions().contains(new BlockPos(1, 65, 1)), "box should be hollow");
    }

    private static void parsesStairs() {
        BuildPlan plan = requirePlan("primitive=stairs origin=0,64,0 size=2,4,4 material=minecraft:cobblestone");
        require(plan.blockCount() == 8, "stairs should contain width*height blocks");
        require(plan.positions().contains(new BlockPos(1, 67, 3)), "stairs should include top tread");
    }

    private static void rejectsMalformedInstructions() {
        requireRejected("");
        requireRejected("build a house");
        requireRejected("primitive=floor origin=0,64,0 size=3,1,3");
        requireRejected("primitive=floor origin=0 64 0 size=3,1,3 material=minecraft:stone");
        requireRejected("primitive=floor origin=0,64,0 size=3,1,3 material=stone");
        requireRejected("primitive=floor origin=0,64,0 size=3,2,3 material=minecraft:stone");
        requireRejected("primitive=line origin=0,64,0 size=3,1,3 material=minecraft:stone");
        requireRejected("primitive=stairs origin=0,64,0 size=2,3,4 material=minecraft:stone");
    }

    private static void rejectsUnboundedPlans() {
        requireRejected("primitive=floor origin=0,64,0 size=17,1,1 material=minecraft:stone");
        requireRejected("primitive=floor origin=0,64,0 size=9,1,8 material=minecraft:stone");
        requireRejected("primitive=wall origin=0,64,0 size=8,9,1 material=minecraft:stone");
        requireRejected("primitive=box origin=0,64,0 size=5,5,5 material=minecraft:stone");
    }

    private static void parsesNamespacedMaterialId() {
        BuildPlan plan = requirePlan("material=minecraft:oak_planks size=1,1,4 origin=-2,70,5 primitive=line");
        require(plan.materialId().toString().equals("minecraft:oak_planks"), "material id should be exact");
        require(plan.origin().equals(new BlockPos(-2, 70, 5)), "origin should allow signed integer coordinates");
    }

    private static BuildPlan requirePlan(String instruction) {
        BuildPlan plan = BuildPlanParser.parseOrNull(instruction);
        require(plan != null, "expected accepted plan: " + instruction);
        return plan;
    }

    private static void requireRejected(String instruction) {
        require(BuildPlanParser.parseOrNull(instruction) == null, "expected rejected plan: " + instruction);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
