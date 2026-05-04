package dev.soffits.openplayer.aicore;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AICorePluginRegistry {
    private final Map<String, CapabilityModule> modules = new LinkedHashMap<>();

    public AICorePluginRegistry(Collection<CapabilityModule> modules) {
        if (modules == null) {
            throw new IllegalArgumentException("capability modules cannot be null");
        }
        for (CapabilityModule module : modules) {
            register(module);
        }
    }

    public static AICorePluginRegistry defaults() {
        return new AICorePluginRegistry(AICoreToolCatalog.defaultModules());
    }

    public void register(AICorePlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        register(plugin.module());
    }

    public void rejectProviderOriginPluginLoad(String requestedName) {
        throw new IllegalArgumentException("provider-origin plugin loading is not allowed: " + requestedName);
    }

    public boolean hasPlugin(String moduleId) {
        return modules.containsKey(moduleId);
    }

    public Optional<CapabilityModule> module(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    public Collection<CapabilityModule> modules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    private void register(CapabilityModule module) {
        if (module == null) {
            throw new IllegalArgumentException("capability module cannot be null");
        }
        if (modules.put(module.id(), module) != null) {
            throw new IllegalArgumentException("duplicate capability module: " + module.id());
        }
    }
}
