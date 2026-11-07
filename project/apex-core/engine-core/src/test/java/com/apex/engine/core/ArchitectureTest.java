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
 *   <li>Core packages ({@code ..core..}) must not depend on enterprise
 *       libraries (Spring, Jackson, Micrometer, Google Cloud, ZXing) —
 *       those belong in Tier 2 adapters only.</li>
 * </ul>
 */
class ArchitectureTest {

    private static final JavaClasses CORE =
            new ClassFileImporter().importPackages("com.apex.engine.core");

    @Test
    void coreDoesNotUseEnterprisePackages() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.boot..",
                        "org.springframework.ai..",
                        "com.google.cloud..",
                        "com.google.mcp..",
                        "org.embabel..",
                        "com.fasterxml.jackson..",
                        "io.micrometer..",
                        "com.zaxxer..",
                        "org.slf4j..")
                .because("Tier 0 core must remain free of enterprise dependencies");
        rule.check(CORE);
    }
}
