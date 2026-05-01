package dev.soffits.openplayer.api;

import java.util.List;

public interface AiPlayerNpcService {
    AiPlayerNpcSession spawn(AiPlayerNpcSpec spec);

    boolean despawn(NpcSessionId sessionId);

    List<AiPlayerNpcSession> listSessions();

    CommandSubmissionResult submitCommand(NpcSessionId sessionId, AiPlayerNpcCommand command);

    NpcSessionStatus status(NpcSessionId sessionId);
}
