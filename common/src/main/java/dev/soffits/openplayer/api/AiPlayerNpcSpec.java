package dev.soffits.openplayer.api;

public record AiPlayerNpcSpec(NpcRoleId roleId, NpcOwnerId ownerId, NpcProfileSpec profile,
                              NpcSpawnLocation spawnLocation) {
    public AiPlayerNpcSpec {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId cannot be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile cannot be null");
        }
        if (spawnLocation == null) {
            throw new IllegalArgumentException("spawnLocation cannot be null");
        }
    }
}
