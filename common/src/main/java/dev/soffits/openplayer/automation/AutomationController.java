package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.intent.CommandIntent;

public interface AutomationController {
    void setOwnerId(NpcOwnerId ownerId);

    AutomationCommandResult submit(CommandIntent intent);

    void tick();

    void stopAll();
}
