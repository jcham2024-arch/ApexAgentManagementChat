package com.apex.engine.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guardrails for the engine core (Tier 0).
 *
 * <p>These rules enforce the tier boundaries defined in
 * {@code CONTEXT.md} §4 and AGENT_RULES §2:
 *
 * <ul>
 *   <li>Engine modules ({@code ..engine..}) must not depend on platform
 *       modules ({@code ..platform..}) — that would let Tier 2 enterprise
 *       code leak into Tier 0.</li>
 *   <li>Core packages ({@code ..core..}) must not depend on enterprise
 *       libraries (Spring, Jackson, Micrometer, Google Cloud, ZXing) —
 *       those belong in Tier 2 adapters only.</li>
 * </ul>
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.apex");

    @Test
    void engineModulesDoNotDependOnPlatform() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage("..platform..")
                .because("Tier 0 engine code must not import Tier 2 platform adapters");
        rule.check(CLASSES);
    }

    @Test
    void coreDoesNotUseEnterprisePackages() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.boot..",
                        "org.springframework.ai..",
                        "com.fasterxml.jackson..",
                        "io.micrometer..",
                        "com.google.cloud..",
                        "com.google.mcp..",
                        "org.embabel..",
                        "com.zaxxer..")
                .because("Tier 0 core must remain free of enterprise dependencies");
        rule.check(CLASSES);
    }
}
