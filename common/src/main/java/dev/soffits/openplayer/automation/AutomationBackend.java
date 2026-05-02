package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;

public interface AutomationBackend {
    String name();

    AutomationBackendStatus status();

    AutomationController createController(OpenPlayerNpcEntity entity);
}
