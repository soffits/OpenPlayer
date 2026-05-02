package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;

public final class DisabledAutomationBackend implements AutomationBackend {
    public static final String NAME = "disabled";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AutomationBackendStatus status() {
        return new AutomationBackendStatus(NAME, AutomationBackendState.DISABLED, "Automation backend disabled");
    }

    @Override
    public AutomationController createController(OpenPlayerNpcEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return new DisabledAutomationController();
    }

    private static final class DisabledAutomationController implements AutomationController {
        @Override
        public void setOwnerId(NpcOwnerId ownerId) {
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId cannot be null");
            }
        }

        @Override
        public AutomationCommandResult submit(CommandIntent intent) {
            if (intent == null) {
                throw new IllegalArgumentException("intent cannot be null");
            }
            return new AutomationCommandResult(AutomationCommandStatus.REJECTED, "Automation backend disabled");
        }

        @Override
        public void tick() {
        }

        @Override
        public void stopAll() {
        }
    }
}
