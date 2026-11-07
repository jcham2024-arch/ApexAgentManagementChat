package com.apex.chat.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural purity test for apex-chat-core (Tier 0).
 * Verifies that no enterprise dependencies leak into the kernel.
 */
class CorePurityTest {

    private static final JavaClasses CORE =
            new ClassFileImporter().importPackages("com.apex.chat.core");

    @Test
    void no_enterprise_imports() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
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

    @Test
    void no_platform_io() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..",
                        "java.nio.file..",
                        "java.net..",
                        "java.lang.reflect..")
                .because("Tier 0 core must not perform direct I/O");
        rule.check(CORE);
    }

    @Test
    void no_executors() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.concurrent.Executors")
                .because("Tier 0 core must not use Executors directly");
        rule.check(CORE);
    }
}
