package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import java.util.UUID;

record RuntimeNpcIdentityKey(UUID ownerId, String roleId, String profileName) {
    RuntimeNpcIdentityKey {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId cannot be null");
        }
        if (profileName == null) {
            throw new IllegalArgumentException("profileName cannot be null");
        }
    }

    static RuntimeNpcIdentityKey from(AiPlayerNpcSpec spec) {
        return from(spec.ownerId().value(), spec.roleId().value(), spec.profile().name());
    }

    static RuntimeNpcIdentityKey from(UUID ownerId, String roleId, String profileName) {
        String identityProfileName = roleId.startsWith(OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX) ? "" : profileName;
        return new RuntimeNpcIdentityKey(ownerId, roleId, identityProfileName);
    }
}
