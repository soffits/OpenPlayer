package dev.soffits.openplayer.aicore;

import java.util.Optional;

public final class AICoreBotFacade {
    private final AICoreBotState state;
    private final ToolRegistry toolRegistry;
    private final AICorePluginRegistry pluginRegistry;
    private final AICoreEventBus eventBus;

    public AICoreBotFacade(AICoreBotState state, ToolRegistry toolRegistry, AICorePluginRegistry pluginRegistry, AICoreEventBus eventBus) {
        if (state == null) {
            throw new IllegalArgumentException("bot state cannot be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("tool registry cannot be null");
        }
        if (pluginRegistry == null) {
            throw new IllegalArgumentException("plugin registry cannot be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("event bus cannot be null");
        }
        this.state = state;
        this.toolRegistry = toolRegistry;
        this.pluginRegistry = pluginRegistry;
        this.eventBus = eventBus;
    }

    public static AICoreBotFacade standalone(String username) {
        return new AICoreBotFacade(AICoreBotState.empty(username), AICoreToolCatalog.registry(), AICorePluginRegistry.defaults(), new AICoreEventBus(128));
    }

    public AICoreBotState state() {
        return state;
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public AICorePluginRegistry pluginRegistry() {
        return pluginRegistry;
    }

    public AICoreEventBus eventBus() {
        return eventBus;
    }

    public ToolResult validate(ToolCall call, ToolValidationContext context) {
        return MinecraftPrimitiveTools.validate(call, context);
    }

    public Optional<AICoreToolDefinition> toolDefinition(String toolName) {
        return AICoreToolCatalog.definition(ToolName.of(toolName));
    }
}
