package dev.soffits.openplayer.automation.navigation;

import java.util.ArrayList;
import java.util.List;

public final class LoadedChunkExplorationMemory {
    public static final int MAX_VISITED_CHUNKS = 64;

    private final List<VisitedChunk> visitedChunks = new ArrayList<>();

    public void clear() {
        visitedChunks.clear();
    }

    public int visitedCount() {
        return visitedChunks.size();
    }

    public boolean isVisited(String dimensionId, int chunkX, int chunkZ) {
        return indexOf(dimensionId, chunkX, chunkZ) >= 0;
    }

    public int recency(String dimensionId, int chunkX, int chunkZ) {
        return indexOf(dimensionId, chunkX, chunkZ);
    }

    public void markVisited(String dimensionId, int chunkX, int chunkZ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId cannot be blank");
        }
        int existing = indexOf(dimensionId, chunkX, chunkZ);
        if (existing >= 0) {
            visitedChunks.remove(existing);
        }
        visitedChunks.add(new VisitedChunk(dimensionId, chunkX, chunkZ));
        while (visitedChunks.size() > MAX_VISITED_CHUNKS) {
            visitedChunks.remove(0);
        }
    }

    private int indexOf(String dimensionId, int chunkX, int chunkZ) {
        if (dimensionId == null) {
            return -1;
        }
        for (int index = 0; index < visitedChunks.size(); index++) {
            VisitedChunk visitedChunk = visitedChunks.get(index);
            if (visitedChunk.dimensionId().equals(dimensionId)
                    && visitedChunk.chunkX() == chunkX
                    && visitedChunk.chunkZ() == chunkZ) {
                return index;
            }
        }
        return -1;
    }

    private record VisitedChunk(String dimensionId, int chunkX, int chunkZ) {
    }
}
