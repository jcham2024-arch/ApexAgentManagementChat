package com.apex.engine.core.port;

import com.apex.engine.core.port.model.SkillManifest;

import java.util.List;

/**
 * Skill resolver: given a skill ID and optional parameters, resolves the
 * executable reference and interface spec. Tier 0.
 */
public interface SkillResolver {

    /**
     * Resolve a skill by ID. Returns the manifest if the skill is known,
     * or empty if not found.
     */
    java.util.Optional<SkillManifest> resolve(String skillId);

    /** Return all known skill IDs. */
    List<String> listSkills();
}
