package dev.soffits.openplayer.api;

public record AiPlayerNpcSpec(NpcRoleId roleId, NpcOwnerId ownerId, NpcProfileSpec profile) {
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
    }
}
