package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.OpenPlayerAutomationConfig;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationCommandResult;
import dev.soffits.openplayer.automation.AutomationCommandStatus;
import dev.soffits.openplayer.automation.AutomationController;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;

final class RuntimeCommandExecutor {
    private final OpenPlayerNpcEntity entity;
    private final AutomationController automationController;

    RuntimeCommandExecutor(OpenPlayerNpcEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        this.entity = entity;
        this.automationController = OpenPlayerAutomationConfig.createBackend().createController(entity);
    }

    void setOwnerId(NpcOwnerId ownerId) {
        automationController.setOwnerId(ownerId);
    }

    CommandSubmissionResult submit(AiPlayerNpcCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        CommandIntent intent = command.intent();
        RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(intent, entity.allowWorldActions());
        if (!validation.isAccepted()) {
            OpenPlayerDebugEvents.record("runtime_validation", "rejected", null, null, null,
                    OpenPlayerDebugEvents.sanitizeDetail(
                            "kind=" + safeIntentKindName(intent) + " message=" + validation.message()
                    ));
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, validation.message());
        }
        AutomationCommandResult result = automationController.submit(intent);
        OpenPlayerDebugEvents.record("automation", result.status().name(), null, null, null,
                "kind=" + intent.kind().name() + " message=" + result.message());
        return commandResult(result);
    }

    private static String safeIntentKindName(CommandIntent intent) {
        if (intent == null) {
            return "unknown";
        }
        return intent.kind().name();
    }

    void tick() {
        automationController.tick();
    }

    void stopAll() {
        automationController.stopAll();
    }

    private static CommandSubmissionResult commandResult(AutomationCommandResult result) {
        CommandSubmissionStatus status = result.status() == AutomationCommandStatus.ACCEPTED
                ? CommandSubmissionStatus.ACCEPTED
                : CommandSubmissionStatus.REJECTED;
        return new CommandSubmissionResult(status, result.message());
    }
}
