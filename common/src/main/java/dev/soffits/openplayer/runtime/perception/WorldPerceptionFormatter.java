package dev.soffits.openplayer.runtime.perception;

import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.HazardEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.PassabilityEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorldPerceptionFormatter {
    public static final int PASSABILITY_LIMIT = 5;
    public static final int HAZARD_LIMIT = 8;
    public static final int SAFE_STAND_LIMIT = 6;
    public static final int CLUSTER_LIMIT = 8;

    private WorldPerceptionFormatter() {
    }

    public static String compact(WorldPerceptionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("worldPerception: {")
                .append("source=").append(snapshot.source())
                .append(",dimension=").append(snapshot.dimension())
                .append(",origin=").append(snapshot.origin().compact())
                .append(",scan={radius=").append(snapshot.scanRadius())
                .append(",verticalDown=").append(snapshot.verticalDown())
                .append(",verticalUp=").append(snapshot.verticalUp())
                .append(",scannedBlocks=").append(snapshot.scannedBlocks())
                .append(",sampledColumns=").append(snapshot.sampledColumns())
                .append(",capped=").append(snapshot.capped())
                .append(",truncated=").append(snapshot.truncated()).append("}")
                .append(",passability=[").append(passability(snapshot.terrain().passability())).append("]")
                .append(",hazards=[").append(hazards(snapshot.hazards())).append("]")
                .append(",safeStand=[").append(safeStand(snapshot.safeStandSpots())).append("]")
                .append(",clusters=[").append(clusters(snapshot.objectClusters())).append("]}");
        return builder.toString();
    }

    private static String passability(List<PassabilityEvidence> values) {
        if (values.isEmpty()) {
            return "none";
        }
        ArrayList<PassabilityEvidence> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(PassabilityEvidence::sector));
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < Math.min(PASSABILITY_LIMIT, sorted.size()); index++) {
            PassabilityEvidence item = sorted.get(index);
            lines.add("{sector=" + item.sector() + ",status=" + item.status() + ",reason=" + item.reason()
                    + ",nearest=" + item.nearestCoordinate().compact() + ",distance=" + rounded(item.distance())
                    + ",heightChange=" + item.heightChange() + ",evidence=" + item.evidence() + "}");
        }
        appendMore(lines, sorted.size(), PASSABILITY_LIMIT);
        return String.join(",", lines);
    }

    private static String hazards(List<HazardEvidence> values) {
        if (values.isEmpty()) {
            return "none";
        }
        ArrayList<HazardEvidence> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingDouble(HazardEvidence::distance).thenComparing(HazardEvidence::kind));
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < Math.min(HAZARD_LIMIT, sorted.size()); index++) {
            HazardEvidence item = sorted.get(index);
            lines.add("{kind=" + item.kind() + ",pos=" + item.position().compact() + ",distance=" + rounded(item.distance())
                    + ",direction=" + item.direction() + ",severity=" + item.severity() + ",reason=" + item.reason()
                    + ",loaded=" + item.loaded() + "}");
        }
        appendMore(lines, sorted.size(), HAZARD_LIMIT);
        return String.join(",", lines);
    }

    private static String safeStand(List<SafeStandSpot> values) {
        if (values.isEmpty()) {
            return "none";
        }
        ArrayList<SafeStandSpot> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingInt(SafeStandSpot::score).reversed().thenComparingDouble(SafeStandSpot::distance));
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < Math.min(SAFE_STAND_LIMIT, sorted.size()); index++) {
            SafeStandSpot item = sorted.get(index);
            lines.add("{pos=" + item.position().compact() + ",reason=" + item.reason() + ",distance="
                    + rounded(item.distance()) + ",score=" + item.score() + "}");
        }
        appendMore(lines, sorted.size(), SAFE_STAND_LIMIT);
        return String.join(",", lines);
    }

    private static String clusters(List<ObjectCluster> values) {
        if (values.isEmpty()) {
            return "none";
        }
        ArrayList<ObjectCluster> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(ObjectCluster::kind).thenComparing(cluster -> cluster.center().compact()));
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < Math.min(CLUSTER_LIMIT, sorted.size()); index++) {
            ObjectCluster item = sorted.get(index);
            lines.add("{kind=" + item.kind() + ",center=" + item.center().compact() + ",count=" + item.count()
                    + ",reachable=" + item.reachable() + ",standable=" + item.standable() + ",confidence="
                    + item.confidence() + ",evidence=[" + blockEvidence(item.evidence()) + "]}");
        }
        appendMore(lines, sorted.size(), CLUSTER_LIMIT);
        return String.join(",", lines);
    }

    private static String blockEvidence(List<BlockEvidence> evidence) {
        if (evidence.isEmpty()) {
            return "none";
        }
        ArrayList<String> values = new ArrayList<>();
        for (BlockEvidence item : evidence) {
            values.add("{id=" + item.id() + ",pos=" + item.position().compact() + ",label=" + item.label() + "}");
        }
        return String.join(",", values);
    }

    private static void appendMore(List<String> lines, int size, int limit) {
        if (size > limit) {
            lines.add("more=" + (size - limit));
        }
    }

    private static String rounded(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
