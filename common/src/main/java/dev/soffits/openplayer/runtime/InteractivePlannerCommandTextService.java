package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcSessionId;
import dev.soffits.openplayer.intent.CommandIntent;
import java.util.function.Consumer;

public interface InteractivePlannerCommandTextService {
    CommandSubmissionResult submitPlannedCommandText(NpcSessionId sessionId, PlannerCommandTextRequest request,
                                                     PlannerCommandTextCallbacks callbacks);

    record PlannerCommandTextRequest(String userRequest, String providerPrompt, String assignmentId, String characterId,
                                     String source) {
        public PlannerCommandTextRequest {
            if (userRequest == null || userRequest.isBlank()) {
                throw new IllegalArgumentException("userRequest cannot be blank");
            }
            if (providerPrompt == null) {
                throw new IllegalArgumentException("providerPrompt cannot be null");
            }
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("source cannot be blank");
            }
        }
    }

    record PlannerCommandTextCallbacks(Consumer<CommandIntent> acceptedIntentRecorder,
                                        Consumer<AiPlayerNpcCommand> submittedCommandRecorder,
                                        Consumer<CommandSubmissionResult> progress,
                                        Consumer<CommandSubmissionResult> completion) {
        public PlannerCommandTextCallbacks {
            if (acceptedIntentRecorder == null) {
                throw new IllegalArgumentException("acceptedIntentRecorder cannot be null");
            }
            if (submittedCommandRecorder == null) {
                throw new IllegalArgumentException("submittedCommandRecorder cannot be null");
            }
            if (progress == null) {
                throw new IllegalArgumentException("progress cannot be null");
            }
            if (completion == null) {
                throw new IllegalArgumentException("completion cannot be null");
            }
        }
    }
}
