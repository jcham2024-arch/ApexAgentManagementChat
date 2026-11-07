package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skill manifest: interface spec, executable reference, dependencies, and
 * version. Pinned into {@link AgentVersionManifest}. Tier 0.
 */
public record SkillManifest(
        String skillId,
        String name,
        String version,
        String description,
        String interfaceSpec,
        String executableRef,
        List<String> dependencies,
        Map<String, String> parameters
) {
    public SkillManifest {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(interfaceSpec, "interfaceSpec");
        Objects.requireNonNull(executableRef, "executableRef");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(parameters, "parameters");
        skillId = skillId.trim();
        if (skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        dependencies = List.copyOf(dependencies);
        parameters = Map.copyOf(parameters);
    }
}
