package com.apex.platform.auth;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guardrails for the apex-auth module (Tier 2).
 *
 * <p>apex-auth is a Tier 2 enterprise adapter. It may use Spring/JWT and
 * other enterprise libraries, but it must not bypass Tier 0 ports by
 * importing Tier 1 adapter implementations directly.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.apex");

    @Test
    void authDoesNotDependOnTier1Adapters() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..engine.tools.impl..",
                        "..engine.store.sqlite..",
                        "..engine.llm.http..")
                .because("Tier 2 auth must access Tier 1 adapters through "
                        + "Tier 0 ports, not direct imports");
        rule.check(CLASSES);
    }

    @Test
    void authDoesNotDependOnOtherEnterpriseAdapters() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..platform.audit..",
                        "..platform.tenant..")
                .because("apex-auth must not import sibling platform modules "
                        + "directly; cross-platform coupling goes through "
                        + "Tier 0 ports");
        rule.check(CLASSES);
    }
}
