package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcSessionId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import java.util.UUID;

final class RuntimeAiPlayerNpcSession implements AiPlayerNpcSession {
    private final RuntimeAiPlayerNpcService service;
    private final NpcSessionId sessionId;
    private AiPlayerNpcSpec spec;
    private UUID entityUuid;

    RuntimeAiPlayerNpcSession(RuntimeAiPlayerNpcService service, NpcSessionId sessionId, AiPlayerNpcSpec spec,
                              UUID entityUuid) {
        this.service = service;
        this.sessionId = sessionId;
        this.spec = spec;
        this.entityUuid = entityUuid;
    }

    @Override
    public NpcSessionId sessionId() {
        return sessionId;
    }

    @Override
    public AiPlayerNpcSpec spec() {
        return spec;
    }

    @Override
    public NpcSessionStatus status() {
        return service.status(sessionId);
    }

    @Override
    public CommandSubmissionResult submitCommand(AiPlayerNpcCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        return service.submitCommand(sessionId, command);
    }

    @Override
    public CommandSubmissionResult submitCommandText(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null");
        }
        return service.submitCommandText(sessionId, input);
    }

    @Override
    public boolean despawn() {
        return service.despawn(sessionId);
    }

    UUID entityUuid() {
        return entityUuid;
    }

    void update(AiPlayerNpcSpec spec, UUID entityUuid) {
        this.spec = spec;
        this.entityUuid = entityUuid;
    }
}
