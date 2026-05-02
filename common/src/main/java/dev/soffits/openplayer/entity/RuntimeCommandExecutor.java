package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.OpenPlayerAutomationConfig;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationCommandResult;
import dev.soffits.openplayer.automation.AutomationCommandStatus;
import dev.soffits.openplayer.automation.AutomationController;

final class RuntimeCommandExecutor {
    private final AutomationController automationController;

    RuntimeCommandExecutor(OpenPlayerNpcEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        this.automationController = OpenPlayerAutomationConfig.createBackend().createController(entity);
    }

    void setOwnerId(NpcOwnerId ownerId) {
        automationController.setOwnerId(ownerId);
    }

    CommandSubmissionResult submit(AiPlayerNpcCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        return commandResult(automationController.submit(command.intent()));
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
