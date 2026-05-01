package dev.soffits.openplayer.api;

import java.util.List;

public final class UnavailableAiPlayerNpcService implements AiPlayerNpcService {
    private static final String UNAVAILABLE_MESSAGE = "AI player NPC runtime support is not available yet";

    @Override
    public AiPlayerNpcSession spawn(AiPlayerNpcSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public boolean despawn(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        return false;
    }

    @Override
    public List<AiPlayerNpcSession> listSessions() {
        return List.of();
    }

    @Override
    public CommandSubmissionResult submitCommand(NpcSessionId sessionId, AiPlayerNpcCommand command) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNAVAILABLE, UNAVAILABLE_MESSAGE);
    }

    @Override
    public NpcSessionStatus status(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        return NpcSessionStatus.UNAVAILABLE;
    }
}
