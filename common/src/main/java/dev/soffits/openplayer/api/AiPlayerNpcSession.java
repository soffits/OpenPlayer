package dev.soffits.openplayer.api;

public interface AiPlayerNpcSession {
    NpcSessionId sessionId();

    AiPlayerNpcSpec spec();

    NpcSessionStatus status();

    CommandSubmissionResult submitCommand(AiPlayerNpcCommand command);

    CommandSubmissionResult submitCommandText(String input);

    boolean despawn();
}
